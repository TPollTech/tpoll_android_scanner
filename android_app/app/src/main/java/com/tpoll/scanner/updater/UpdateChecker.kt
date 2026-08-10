// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.updater

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class UpdateInfo(
    val version_code: Int = 0,
    val version_name: String = "",
    val changelog: String = "",
    val download_url: String = "",
    val apk_url: String = "",
    val sha256: String = "",
    val size_bytes: Long = 0L,
    val released_at: String = "",
    val min_version_code: Int = 1
) {
    fun isNewerThan(installedVersionCode: Long): Boolean =
        version_code.toLong() > installedVersionCode

    fun isMandatoryFor(installedVersionCode: Long): Boolean =
        installedVersionCode < min_version_code.toLong()
}

sealed class UpdateResult {
    data class Available(
        val info: UpdateInfo,
        val installedVersion: InstalledAppVersion
    ) : UpdateResult()

    data class UpToDate(val installedVersion: InstalledAppVersion) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

object UpdateManifestValidator {
    private val SHA_256 = Regex("^[0-9a-fA-F]{64}$")
    private const val MAX_APK_BYTES = 250L * 1024L * 1024L

    fun error(info: UpdateInfo): String? = when {
        info.version_code <= 0 -> "O manifesto não informa uma versão válida."
        info.version_name.isBlank() -> "O manifesto não informa o nome da versão."
        info.min_version_code <= 0 -> "O manifesto informa uma versão mínima inválida."
        info.min_version_code > info.version_code ->
            "O manifesto exige uma versão mínima maior que a atualização disponível."
        !isHttps(info.apk_url) -> "O manifesto não informa um endereço HTTPS para o APK."
        info.apk_url != expectedApkUrl(info.version_name) ->
            "O manifesto não aponta para o APK oficial desta versão."
        !isHttps(info.download_url) -> "O manifesto não informa uma página HTTPS de download."
        !SHA_256.matches(info.sha256) -> "O manifesto não contém um SHA-256 válido."
        info.size_bytes !in 1..MAX_APK_BYTES -> "O manifesto informa um tamanho de APK inválido."
        else -> null
    }

    private fun isHttps(value: String): Boolean = runCatching {
        URL(value).protocol.equals("https", ignoreCase = true)
    }.getOrDefault(false)

    private fun expectedApkUrl(versionName: String): String =
        "https://github.com/TPollTech/tpoll_android_scanner/releases/download/" +
            "v$versionName/TPollScanner-$versionName-release.apk"
}

class UpdateChecker(private val context: Context) {

    suspend fun checkForUpdates(): UpdateResult = withContext(Dispatchers.IO) {
        val installedVersion = runCatching { InstalledAppVersion.read(context) }
            .getOrElse {
                return@withContext UpdateResult.Error(
                    "Não foi possível identificar a versão instalada."
                )
            }

        val connection = try {
            (URL(UPDATE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "TPollScanner/${installedVersion.name}")
            }
        } catch (_: Exception) {
            return@withContext UpdateResult.Error("Endereço de atualização inválido.")
        }

        try {
            val responseCode = connection.responseCode
            if (!connection.url.protocol.equals("https", ignoreCase = true)) {
                return@withContext UpdateResult.Error(
                    "O servidor redirecionou a verificação para uma conexão insegura."
                )
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateResult.Error("Servidor retornou código $responseCode.")
            }

            val declaredSize = connection.contentLengthLong
            if (declaredSize > MAX_MANIFEST_BYTES) {
                return@withContext UpdateResult.Error("Manifesto de atualização maior que o permitido.")
            }

            val response = connection.inputStream.use(::readManifest)
            val updateInfo = runCatching { Gson().fromJson(response, UpdateInfo::class.java) }
                .getOrNull()
                ?: return@withContext UpdateResult.Error("Erro ao ler dados de versão.")

            UpdateManifestValidator.error(updateInfo)?.let { validationError ->
                return@withContext UpdateResult.Error(validationError)
            }

            markChecked(context)
            if (updateInfo.isNewerThan(installedVersion.code)) {
                UpdateResult.Available(updateInfo, installedVersion)
            } else {
                UpdateResult.UpToDate(installedVersion)
            }
        } catch (_: java.net.UnknownHostException) {
            UpdateResult.Error("Sem conexão com a internet.")
        } catch (_: java.net.SocketTimeoutException) {
            UpdateResult.Error("Tempo limite excedido. Verifique sua internet.")
        } catch (_: ManifestTooLargeException) {
            UpdateResult.Error("Manifesto de atualização maior que o permitido.")
        } catch (_: Exception) {
            UpdateResult.Error("Erro de conexão.")
        } finally {
            connection.disconnect()
        }
    }

    private fun readManifest(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_MANIFEST_BYTES) throw ManifestTooLargeException()
            output.write(buffer, 0, read)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private class ManifestTooLargeException : Exception()

    companion object {
        private const val UPDATE_URL =
            "https://raw.githubusercontent.com/TPollTech/tpoll_android_scanner/main/update.json"
        private const val PREF_NAME = "update_prefs"
        private const val KEY_LAST_CHECK = "last_successful_update_check"
        private const val CHECK_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L
        private const val MAX_MANIFEST_BYTES = 128 * 1024

        fun shouldCheck(context: Context): Boolean {
            val lastCheck = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_CHECK, 0L)
            return System.currentTimeMillis() - lastCheck > CHECK_INTERVAL_MILLIS
        }

        private fun markChecked(context: Context) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .apply()
        }
    }
}
