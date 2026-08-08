package com.tpoll.scanner.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed class ApkInstallRequestResult {
    data class Submitted(val silentInstallRequested: Boolean) : ApkInstallRequestResult()
    data object PermissionRequired : ApkInstallRequestResult()
    data class Failed(
        val message: String,
        val requiresOneTimeReinstall: Boolean = false
    ) : ApkInstallRequestResult()
}

object ApkInstaller {

    private const val MAX_APK_BYTES = 250L * 1024L * 1024L
    const val ACTION_INSTALL_STATUS = "com.tpoll.scanner.action.UPDATE_INSTALL_STATUS"

    fun canRequestPackageInstalls(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        expectedVersionCode: Int
    ): ApkInstallRequestResult = withContext(Dispatchers.IO) {
        if (!canRequestPackageInstalls(context)) {
            return@withContext ApkInstallRequestResult.PermissionRequired
        }

        val url = runCatching { URL(apkUrl) }.getOrElse {
            return@withContext ApkInstallRequestResult.Failed("Endereço da atualização inválido.")
        }
        if (!url.protocol.equals("https", ignoreCase = true)) {
            return@withContext ApkInstallRequestResult.Failed(
                "A atualização foi recusada porque o download não usa HTTPS."
            )
        }

        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.cacheDir
        val partialFile = File(downloadsDir, "TPollScanner-update.apk.part")
        val apkFile = File(downloadsDir, "TPollScanner-update.apk")

        try {
            partialFile.delete()
            apkFile.delete()
            download(url, partialFile)

            if (!partialFile.renameTo(apkFile)) {
                return@withContext ApkInstallRequestResult.Failed(
                    "Não foi possível preparar o arquivo da atualização."
                )
            }

            validateApk(context, apkFile, expectedVersionCode)?.let { validationFailure ->
                return@withContext validationFailure
            }

            submitInstall(context, apkFile)
        } catch (_: java.net.UnknownHostException) {
            ApkInstallRequestResult.Failed("Sem conexão com a internet.")
        } catch (_: java.net.SocketTimeoutException) {
            ApkInstallRequestResult.Failed("O download demorou demais. Tente novamente.")
        } catch (error: Exception) {
            ApkInstallRequestResult.Failed(
                "Não foi possível baixar a atualização: ${error.localizedMessage ?: error.javaClass.simpleName}"
            )
        } finally {
            partialFile.delete()
            apkFile.delete()
        }
    }

    private fun download(url: URL, destination: File) {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            connect()
        }

        try {
            if (!connection.url.protocol.equals("https", ignoreCase = true)) {
                throw IllegalStateException("O redirecionamento do download não usa HTTPS.")
            }
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("O servidor retornou HTTP ${connection.responseCode}.")
            }

            val declaredSize = connection.contentLengthLong
            if (declaredSize > MAX_APK_BYTES) {
                throw IllegalStateException("O arquivo informado é maior que o limite permitido.")
            }

            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_APK_BYTES) {
                            throw IllegalStateException("A atualização excedeu o limite permitido.")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }

            if (!destination.exists() || destination.length() == 0L) {
                throw IllegalStateException("O servidor entregou um arquivo vazio.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun validateApk(
        context: Context,
        apkFile: File,
        expectedVersionCode: Int
    ): ApkInstallRequestResult.Failed? {
        val packageManager = context.packageManager
        val archiveInfo = getArchivePackageInfo(packageManager, apkFile)
            ?: return ApkInstallRequestResult.Failed("O arquivo baixado não é um APK Android válido.")
        val installedInfo = getInstalledPackageInfo(packageManager, context.packageName)
            ?: return ApkInstallRequestResult.Failed("Não foi possível validar a instalação atual.")

        if (archiveInfo.packageName != context.packageName) {
            return ApkInstallRequestResult.Failed(
                "A atualização pertence a outro aplicativo e foi bloqueada."
            )
        }

        val archiveVersionCode = archiveInfo.versionCodeCompat()
        val installedVersionCode = installedInfo.versionCodeCompat()
        if (archiveVersionCode != expectedVersionCode.toLong()) {
            return ApkInstallRequestResult.Failed(
                "A versão do APK não corresponde à atualização anunciada."
            )
        }

        if (archiveVersionCode <= installedVersionCode) {
            return ApkInstallRequestResult.Failed("O APK baixado não é mais novo que o app instalado.")
        }

        val installedCertificates = signingCertificateDigests(installedInfo)
        val archiveCertificates = signingCertificateDigests(archiveInfo)
        if (installedCertificates.isEmpty() || archiveCertificates.isEmpty()) {
            return ApkInstallRequestResult.Failed(
                "Não foi possível confirmar a assinatura digital da atualização."
            )
        }
        if (installedCertificates.intersect(archiveCertificates).isEmpty()) {
            return ApkInstallRequestResult.Failed(
                message = "Esta instalação usa uma chave antiga incompatível. " +
                    "Faça uma única reinstalação pela versão oficial; as próximas atualizações " +
                    "serão instaladas normalmente.",
                requiresOneTimeReinstall = true
            )
        }

        return null
    }

    private fun submitInstall(context: Context, apkFile: File): ApkInstallRequestResult {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apkFile.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            setOriginatingUri(Uri.parse("https://tpolltech.github.io/tpoll_android_scanner/"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

        var sessionId: Int? = null
        return try {
            sessionId = packageInstaller.createSession(params)
            packageInstaller.openSession(sessionId).use { session ->
                apkFile.inputStream().use { input ->
                    session.openWrite("TPollScanner-update.apk", 0, apkFile.length()).use { output ->
                        input.copyTo(output, 64 * 1024)
                        session.fsync(output)
                    }
                }

                val statusIntent = Intent(context, UpdateInstallReceiver::class.java).apply {
                    action = ACTION_INSTALL_STATUS
                }
                val statusPendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    statusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(statusPendingIntent.intentSender)
            }
            ApkInstallRequestResult.Submitted(
                silentInstallRequested = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            )
        } catch (_: SecurityException) {
            sessionId?.let(packageInstaller::abandonSession)
            ApkInstallRequestResult.PermissionRequired
        } catch (error: Exception) {
            sessionId?.let(packageInstaller::abandonSession)
            ApkInstallRequestResult.Failed(
                "O Android recusou a solicitação de instalação: " +
                    (error.localizedMessage ?: error.javaClass.simpleName)
            )
        }
    }

    private fun getArchivePackageInfo(packageManager: PackageManager, apkFile: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        }

    private fun getInstalledPackageInfo(
        packageManager: PackageManager,
        packageName: String
    ): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
    }.getOrNull()

    private fun signingCertificateDigests(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }

        return signatures.orEmpty().mapTo(linkedSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }
    }

    private fun PackageInfo.versionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }
}
