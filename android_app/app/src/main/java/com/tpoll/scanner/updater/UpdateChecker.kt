package com.tpoll.scanner.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
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
        private const val GITHUB_RELEASES_URL = "https://github.com/TPollTech/tpoll_android_scanner/releases"
        private const val PREF_NAME = "update_prefs"
        private const val KEY_LAST_CHECK = "last_update_check"

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
            return System.currentTimeMillis() - lastCheck > 24 * 60 * 60 * 1000L
        }

        fun markChecked(context: Context) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .apply()
        }
    }

    suspend fun checkForUpdates(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(UPDATE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = true

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()
            connection.disconnect()

            val gson = Gson()
            val updateInfo = gson.fromJson(response, UpdateInfo::class.java)

            if (updateInfo.version_code > UpdateInfo.currentVersionCode) {
                UpdateResult.Available(updateInfo)
            } else {
                UpdateResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Erro ao verificar atualização")
        }
    }

    fun openDownloadUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) { }
    }

    fun openReleasesPage(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) { }
    }
}
