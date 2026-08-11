// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
package com.tpoll.scanner.updater

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class UpdateInfo(
    @SerializedName(value = "versionCode", alternate = ["version_code"])
    val versionCode: Int = 0,
    @SerializedName(value = "versionName", alternate = ["version_name"])
    val versionName: String = "",
    @SerializedName(value = "apkUrl", alternate = ["apk_url"])
    val apkUrl: String = "",
    val sha256: String = "",
    val mandatory: Boolean = false,
    @SerializedName(value = "releaseNotes", alternate = ["release_notes"])
    val releaseNotes: List<String> = emptyList(),
    @SerializedName(value = "downloadUrl", alternate = ["download_url"])
    val downloadUrl: String = "",
    @SerializedName(value = "sizeBytes", alternate = ["size_bytes"])
    val sizeBytes: Long = 0L,
    @SerializedName(value = "releasedAt", alternate = ["released_at"])
    val releasedAt: String = "",
    @SerializedName(value = "minVersionCode", alternate = ["min_version_code"])
    val minVersionCode: Int = 1,
    @SerializedName("changelog")
    val legacyChangelog: String = ""
) {
    fun isNewerThan(installedVersionCode: Long): Boolean =
        versionCode.toLong() > installedVersionCode

    fun isMandatoryFor(installedVersionCode: Long): Boolean =
        mandatory || installedVersionCode < minVersionCode.toLong()

    fun notesForDisplay(): List<String> = releaseNotes
        .map(String::trim)
        .filter(String::isNotBlank)
        .ifEmpty {
            legacyChangelog.lineSequence()
                .map { it.trim().removePrefix("-").trim() }
                .filter { it.isNotBlank() }
                .toList()
        }
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
    private val VERSION_NAME = Regex("^\\d+\\.\\d+\\.\\d+$")
    private const val MAX_APK_BYTES = 250L * 1024L * 1024L

    fun error(info: UpdateInfo): String? = when {
        info.versionCode <= 0 -> "O manifesto não informa uma versão válida."
        !VERSION_NAME.matches(info.versionName) ->
            "O manifesto não informa o nome da versão no formato correto."
        info.minVersionCode <= 0 -> "O manifesto informa uma versão mínima inválida."
        info.minVersionCode > info.versionCode ->
            "O manifesto exige uma versão mínima maior que a atualização disponível."
        !isHttps(info.apkUrl) -> "O manifesto não informa um endereço HTTPS para o APK."
        info.apkUrl != expectedApkUrl(info.versionName) ->
            "O manifesto não aponta para o APK oficial desta versão."
        !isHttps(info.downloadUrl) -> "O manifesto não informa uma página HTTPS de download."
        !SHA_256.matches(info.sha256) -> "O manifesto não contém um SHA-256 válido."
        info.sizeBytes !in 1..MAX_APK_BYTES -> "O manifesto informa um tamanho de APK inválido."
        info.notesForDisplay().isEmpty() -> "O manifesto não informa as notas da versão."
        else -> null
    }

    private fun isHttps(value: String): Boolean = runCatching {
        URL(value).protocol.equals("https", ignoreCase = true)
    }.getOrDefault(false)

    fun expectedApkUrl(versionName: String): String =
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
                setRequestProperty("Cache-Control", "no-cache")
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
        internal const val CHECK_INTERVAL_MILLIS = 6 * 60 * 60 * 1000L
        private const val MAX_MANIFEST_BYTES = 128 * 1024

        fun shouldCheck(context: Context): Boolean {
            val lastCheck = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_CHECK, 0L)
            return shouldCheckAt(lastCheck, System.currentTimeMillis())
        }

        internal fun shouldCheckAt(lastCheck: Long, now: Long): Boolean =
            lastCheck <= 0L || now < lastCheck || now - lastCheck >= CHECK_INTERVAL_MILLIS

        private fun markChecked(context: Context) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .apply()
        }
    }
}
