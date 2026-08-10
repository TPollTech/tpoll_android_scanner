package com.tpoll.scanner.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed class ApkInstallRequestResult {
    data class Submitted(val silentInstallRequested: Boolean) : ApkInstallRequestResult()
    data object PermissionRequired : ApkInstallRequestResult()
    data class Failed(
        val message: String,
        val requiresOneTimeReinstall: Boolean = false,
        val retryable: Boolean = false
    ) : ApkInstallRequestResult()
}

object ApkInstaller {

    private const val MAX_APK_BYTES = 250L * 1024L * 1024L
    private val SHA_256_PATTERN = Regex("^[0-9a-fA-F]{64}$")

    const val ACTION_INSTALL_STATUS = "com.tpoll.scanner.action.UPDATE_INSTALL_STATUS"
    const val EXTRA_VERSION_CODE = "update_version_code"
    const val EXTRA_VERSION_NAME = "update_version_name"

    fun canRequestPackageInstalls(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        expectedVersionCode: Int,
        expectedSha256: String,
        expectedSizeBytes: Long
    ): ApkInstallRequestResult = withContext(Dispatchers.IO) {
        if (!canRequestPackageInstalls(context)) {
            return@withContext ApkInstallRequestResult.PermissionRequired
        }
        if (!SHA_256_PATTERN.matches(expectedSha256)) {
            return@withContext ApkInstallRequestResult.Failed(
                "A atualização foi bloqueada porque o manifesto não contém um SHA-256 válido."
            )
        }
        if (expectedSizeBytes !in 1..MAX_APK_BYTES) {
            return@withContext ApkInstallRequestResult.Failed(
                "A atualização foi bloqueada porque o tamanho informado é inválido."
            )
        }

        val url = runCatching { URL(apkUrl) }.getOrElse {
            return@withContext ApkInstallRequestResult.Failed(
                "Endereço da atualização inválido."
            )
        }
        if (!url.protocol.equals("https", ignoreCase = true)) {
            return@withContext ApkInstallRequestResult.Failed(
                "A atualização foi recusada porque o download não usa HTTPS."
            )
        }

        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val partialFile = File(updatesDir, "TPollScanner-$expectedVersionCode.apk.part")
        val apkFile = File(updatesDir, "TPollScanner-$expectedVersionCode.apk")
        updatesDir.listFiles()
            .orEmpty()
            .filterNot { it == partialFile || it == apkFile }
            .forEach(File::delete)
        var keepPartialForRetry = false

        try {
            apkFile.delete()
            val receipt = download(
                url = url,
                destination = partialFile,
                expectedSizeBytes = expectedSizeBytes
            )
            if (!receipt.sha256.equals(expectedSha256, ignoreCase = true)) {
                partialFile.delete()
                return@withContext ApkInstallRequestResult.Failed(
                    "Integridade do APK comprometida. O SHA-256 não corresponde."
                )
            }
            if (!partialFile.renameTo(apkFile)) {
                return@withContext ApkInstallRequestResult.Failed(
                    "Não foi possível preparar o arquivo da atualização."
                )
            }

            validateApk(context, apkFile, expectedVersionCode)?.let { validationFailure ->
                return@withContext validationFailure
            }

            submitInstall(
                context = context,
                apkFile = apkFile,
                versionCode = expectedVersionCode,
                versionName = extractVersionName(context, apkFile)
            )
        } catch (_: java.net.UnknownHostException) {
            keepPartialForRetry = true
            ApkInstallRequestResult.Failed("Sem conexão com a internet.", retryable = true)
        } catch (_: java.net.SocketTimeoutException) {
            keepPartialForRetry = true
            ApkInstallRequestResult.Failed(
                "O download demorou demais. Uma nova tentativa continuará de onde parou.",
                retryable = true
            )
        } catch (error: DownloadException) {
            keepPartialForRetry = error.retryable
            ApkInstallRequestResult.Failed(error.message.orEmpty(), retryable = error.retryable)
        } catch (error: Exception) {
            ApkInstallRequestResult.Failed(
                "Não foi possível baixar a atualização: " +
                    (error.localizedMessage ?: error.javaClass.simpleName)
            )
        } finally {
            apkFile.delete()
            if (!keepPartialForRetry) partialFile.delete()
        }
    }

    private fun download(
        url: URL,
        destination: File,
        expectedSizeBytes: Long
    ): DownloadReceipt {
        if (destination.exists() && destination.length() == expectedSizeBytes) {
            return DownloadReceipt(destination.length(), sha256(destination))
        }
        if (destination.length() > expectedSizeBytes) destination.delete()

        val resumeOffset = destination.takeIf(File::exists)?.length() ?: 0L
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept-Encoding", "identity")
            if (resumeOffset > 0L) setRequestProperty("Range", "bytes=$resumeOffset-")
            connect()
        }

        try {
            if (!connection.url.protocol.equals("https", ignoreCase = true)) {
                throw DownloadException(
                    "O redirecionamento do download não usa HTTPS.",
                    retryable = false
                )
            }

            val responseCode = connection.responseCode
            val resumed = resumeOffset > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL
            if (responseCode !in 200..299) {
                throw DownloadException(
                    message = "O servidor retornou HTTP $responseCode.",
                    retryable = responseCode == 408 || responseCode == 429 || responseCode >= 500
                )
            }

            val initialSize = if (resumed) resumeOffset else 0L
            val declaredSize = connection.contentLengthLong
            if (declaredSize > 0L && initialSize + declaredSize > MAX_APK_BYTES) {
                throw DownloadException(
                    "O arquivo informado é maior que o limite permitido.",
                    retryable = false
                )
            }

            connection.inputStream.use { input ->
                FileOutputStream(destination, resumed).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = initialSize
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_APK_BYTES || total > expectedSizeBytes) {
                            throw DownloadException(
                                "A atualização excedeu o tamanho anunciado.",
                                retryable = false
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            }

            val actualSize = destination.length()
            if (actualSize != expectedSizeBytes) {
                throw DownloadException(
                    "Download incompleto: esperado $expectedSizeBytes bytes, recebido $actualSize.",
                    retryable = actualSize < expectedSizeBytes
                )
            }
            return DownloadReceipt(actualSize, sha256(destination))
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
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
                    "Baixe a versão oficial pelo navegador, desinstale esta versão e instale " +
                    "o arquivo baixado. Isso será necessário apenas uma vez.",
                requiresOneTimeReinstall = true
            )
        }

        return null
    }

    private fun submitInstall(
        context: Context,
        apkFile: File,
        versionCode: Int,
        versionName: String
    ): ApkInstallRequestResult {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apkFile.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            setOriginatingUri(Uri.parse("https://github.com/TPollTech/tpoll_android_scanner/releases"))
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
                    putExtra(EXTRA_VERSION_CODE, versionCode)
                    putExtra(EXTRA_VERSION_NAME, versionName)
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

    private fun extractVersionName(context: Context, apkFile: File): String =
        getArchivePackageInfo(context.packageManager, apkFile)?.versionName.orEmpty()

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

    private data class DownloadReceipt(val bytes: Long, val sha256: String)

    private class DownloadException(message: String, val retryable: Boolean) : Exception(message)
}
