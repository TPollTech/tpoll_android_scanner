// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version_code: Int = 0,
    val version_name: String = "",
    val changelog: String = "",
    val download_url: String = "",
    val apk_url: String = ""
) {
    val isNewer: Boolean get() = version_code > currentVersionCode

    companion object {
        var currentVersionCode: Int = 1
        var currentVersionName: String = "1.0.0"
    }
}

sealed class UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult()
    data object UpToDate : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

class UpdateChecker {

    companion object {
        private const val UPDATE_URL = "https://raw.githubusercontent.com/TPollTech/tpoll_android_scanner/main/update.json"
        private const val PREF_NAME = "update_prefs"
        private const val KEY_LAST_CHECK = "last_update_check"
        private const val KEY_RETRY_COUNT = "retry_count"
        private const val MAX_RETRIES = 3

        fun init(context: Context) {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            UpdateInfo.currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode
            }
            UpdateInfo.currentVersionName = pkgInfo.versionName ?: "1.0.0"
        }

        fun shouldCheck(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
            val retries = prefs.getInt(KEY_RETRY_COUNT, 0)
            if (retries > 0) {
                return System.currentTimeMillis() - lastCheck > 5 * 60 * 1000L
            }
            return System.currentTimeMillis() - lastCheck > 24 * 60 * 60 * 1000L
        }

        fun markChecked(context: Context) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .putInt(KEY_RETRY_COUNT, 0)
                .apply()
        }

        private fun markRetry(context: Context) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val retries = prefs.getInt(KEY_RETRY_COUNT, 0) + 1
            prefs.edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .putInt(KEY_RETRY_COUNT, retries)
                .apply()
        }

        fun canRetry(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_RETRY_COUNT, 0) < MAX_RETRIES
        }
    }

    suspend fun checkForUpdates(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(UPDATE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = true

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateResult.Error("Servidor retornou código $responseCode")
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()
            connection.disconnect()

            val gson = Gson()
            val updateInfo = try {
                gson.fromJson(response, UpdateInfo::class.java)
            } catch (e: Exception) {
                return@withContext UpdateResult.Error("Erro ao ler dados de versão")
            }

            if (updateInfo.version_code > UpdateInfo.currentVersionCode) {
                UpdateResult.Available(updateInfo)
            } else {
                UpdateResult.UpToDate
            }
        } catch (e: java.net.UnknownHostException) {
            UpdateResult.Error("Sem conexão com a internet")
        } catch (e: java.net.SocketTimeoutException) {
            UpdateResult.Error("Tempo limite excedido. Verifique sua internet")
        } catch (e: Exception) {
            UpdateResult.Error("Erro de conexão")
        }
    }

    suspend fun checkForUpdatesWithRetry(context: Context): UpdateResult {
        var result: UpdateResult
        var attempt = 0
        do {
            result = checkForUpdates()
            if (result is UpdateResult.Error && attempt < MAX_RETRIES) {
                attempt++
                markRetry(context)
                delay(2000L * attempt)
            } else {
                break
            }
        } while (true)

        if (result is UpdateResult.Available || result is UpdateResult.UpToDate) {
            markChecked(context)
        }
        return result
    }

    fun openDownloadUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) { }
    }
}
