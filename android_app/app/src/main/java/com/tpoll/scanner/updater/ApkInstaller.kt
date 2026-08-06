package com.tpoll.scanner.updater

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ApkInstaller {

    fun isInstallSourceTrusted(context: Context): Boolean {
        val installSource = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName)
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
            return true
        }
        val installer = installSource.installingPackageName
        return installer == context.packageName ||
            installer == "com.android.vending" ||
            installer == "com.google.android.feedback"
    }

    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        onStarted: () -> Unit = {},
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        try {
            val fileName = "TPollScanner-update.apk"
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir
            val apkFile = File(downloadsDir, fileName)

            onStarted()

            val url = java.net.URL(apkUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.connect()

            val inputStream = connection.inputStream
            apkFile.outputStream().use { output ->
                inputStream.copyTo(output, bufferSize = 64 * 1024)
            }
            inputStream.close()
            connection.disconnect()

            if (!apkFile.exists() || apkFile.length() == 0L) {
                onError("Download falhou: arquivo vazio.")
                return@withContext
            }

            val installIntent = createInstallIntent(context, apkFile)

            try {
                context.startActivity(installIntent)
                onSuccess()
            } catch (e: Exception) {
                onError("Não foi possível iniciar a instalação: ${e.localizedMessage}")
            }

        } catch (e: java.net.UnknownHostException) {
            onError("Sem conexão com a internet.")
        } catch (e: java.net.SocketTimeoutException) {
            onError("Tempo limite excedido. Verifique sua internet.")
        } catch (e: Exception) {
            onError("Erro ao baixar: ${e.localizedMessage}")
        }
    }

    private fun createInstallIntent(context: Context, apkFile: File): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        } else {
            val uri = Uri.fromFile(apkFile)
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }
}
