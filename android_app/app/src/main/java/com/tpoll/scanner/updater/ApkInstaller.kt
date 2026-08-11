package com.tpoll.scanner.updater

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f else {
            (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        }
}

sealed class ApkPreparationResult {
    data class Ready(
        val versionCode: Int,
        val versionName: String,
        val fileSize: Long
    ) : ApkPreparationResult()

    data class Failed(
        val message: String,
        val requiresOneTimeReinstall: Boolean = false,
        val retryable: Boolean = false
    ) : ApkPreparationResult()
}

sealed class ApkInstallLaunchResult {
    data object Launched : ApkInstallLaunchResult()
    data object PermissionRequired : ApkInstallLaunchResult()
    data class Failed(val message: String) : ApkInstallLaunchResult()
}

object ApkInstaller {

    private const val EXPECTED_PACKAGE_NAME = "com.tpoll.scanner"
    internal const val EXPECTED_RELEASE_CERT_SHA256 =
        "603a48c1b31271fe37cf0502f083a741cf5532f170a3fa3617f48cb6a5f0b6d5"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val MAX_APK_BYTES = 250L * 1024L * 1024L
    private val SHA_256_PATTERN = Regex("^[0-9a-fA-F]{64}$")
    private val operationMutex = Mutex()

    fun canRequestPackageInstalls(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
    )

    suspend fun downloadAndValidate(
        context: Context,
        info: UpdateInfo,
        onProgress: suspend (DownloadProgress) -> Unit = {}
    ): ApkPreparationResult = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            UpdateManifestValidator.error(info)?.let { error ->
                return@withContext ApkPreparationResult.Failed(error)
            }
            if (context.packageName != EXPECTED_PACKAGE_NAME) {
                return@withContext ApkPreparationResult.Failed(
                    "A atualização foi bloqueada porque o aplicativo instalado tem outro pacote."
                )
            }
            if (!SHA_256_PATTERN.matches(info.sha256)) {
                return@withContext ApkPreparationResult.Failed(
                    "A atualização foi bloqueada porque o SHA-256 é inválido."
                )
            }
            if (info.sizeBytes !in 1..MAX_APK_BYTES) {
                return@withContext ApkPreparationResult.Failed(
                    "A atualização foi bloqueada porque o tamanho informado é inválido."
                )
            }

            val url = runCatching { URL(info.apkUrl) }.getOrElse {
                return@withContext ApkPreparationResult.Failed(
                    "Endereço da atualização inválido."
                )
            }
            if (!url.protocol.equals("https", ignoreCase = true)) {
                return@withContext ApkPreparationResult.Failed(
                    "A atualização foi recusada porque o download não usa HTTPS."
                )
            }

            val updatesDir = updatesDirectory(context)
            val partialFile = File(updatesDir, "TPollScanner-${info.versionCode}.apk.part")
            val apkFile = preparedFile(context, info)
            cleanupExcept(updatesDir, setOf(partialFile, apkFile))

            if (apkFile.isFile) {
                val cachedFailure = validateApk(context, apkFile, info)
                if (cachedFailure == null) {
                    onProgress(DownloadProgress(apkFile.length(), info.sizeBytes))
                    return@withContext ApkPreparationResult.Ready(
                        info.versionCode,
                        info.versionName,
                        apkFile.length()
                    )
                }
                apkFile.delete()
            }

            var keepPartialForRetry = false
            try {
                download(
                    url = url,
                    destination = partialFile,
                    expectedSizeBytes = info.sizeBytes,
                    onProgress = onProgress
                )

                val actualHash = sha256(partialFile)
                if (!actualHash.equals(info.sha256, ignoreCase = true)) {
                    partialFile.delete()
                    return@withContext ApkPreparationResult.Failed(
                        "Integridade do APK comprometida. O SHA-256 não corresponde."
                    )
                }
                apkFile.delete()
                if (!partialFile.renameTo(apkFile)) {
                    return@withContext ApkPreparationResult.Failed(
                        "Não foi possível preparar o arquivo da atualização."
                    )
                }

                validateApk(context, apkFile, info)?.let { validationFailure ->
                    apkFile.delete()
                    return@withContext validationFailure
                }

                onProgress(DownloadProgress(apkFile.length(), info.sizeBytes))
                ApkPreparationResult.Ready(info.versionCode, info.versionName, apkFile.length())
            } catch (_: java.net.UnknownHostException) {
                keepPartialForRetry = true
                ApkPreparationResult.Failed("Sem conexão com a internet.", retryable = true)
            } catch (_: java.net.SocketTimeoutException) {
                keepPartialForRetry = true
                ApkPreparationResult.Failed(
                    "O download demorou demais. Uma nova tentativa continuará de onde parou.",
                    retryable = true
                )
            } catch (error: DownloadException) {
                keepPartialForRetry = error.retryable
                ApkPreparationResult.Failed(error.message.orEmpty(), retryable = error.retryable)
            } catch (error: Exception) {
                ApkPreparationResult.Failed(
                    "Não foi possível baixar a atualização: " +
                        (error.localizedMessage ?: error.javaClass.simpleName)
                )
            } finally {
                if (!keepPartialForRetry) partialFile.delete()
            }
        }
    }

    suspend fun launchInstaller(
        context: Context,
        info: UpdateInfo
    ): ApkInstallLaunchResult {
        if (!canRequestPackageInstalls(context)) {
            return ApkInstallLaunchResult.PermissionRequired
        }

        val apkFile = preparedFile(context, info)
        val validationFailure = withContext(Dispatchers.IO) {
            validateApk(context, apkFile, info)
        }
        if (validationFailure != null) {
            return ApkInstallLaunchResult.Failed(validationFailure.message)
        }

        return try {
            val intent = createInstallerIntent(context, info)
                ?: return ApkInstallLaunchResult.Failed(
                    "O APK validado não está mais disponível. Baixe novamente."
                )
            withContext(Dispatchers.Main.immediate) {
                context.startActivity(intent)
            }
            ApkInstallLaunchResult.Launched
        } catch (error: Exception) {
            ApkInstallLaunchResult.Failed(
                "Não foi possível abrir o instalador do Android: " +
                    (error.localizedMessage ?: error.javaClass.simpleName)
            )
        }
    }

    fun createInstallerIntent(context: Context, info: UpdateInfo): Intent? {
        val apkFile = preparedFile(context, info).takeIf(File::isFile) ?: return null
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updateprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, APK_MIME_TYPE)
            clipData = ClipData.newRawUri("TPoll Scanner update", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun isPrepared(context: Context, info: UpdateInfo): Boolean {
        val file = preparedFile(context, info)
        return file.isFile && file.length() == info.sizeBytes
    }

    fun clearCachedUpdates(context: Context) {
        updatesDirectory(context).listFiles().orEmpty().forEach(File::delete)
    }

    private suspend fun download(
        url: URL,
        destination: File,
        expectedSizeBytes: Long,
        onProgress: suspend (DownloadProgress) -> Unit
    ) {
        if (destination.exists() && destination.length() == expectedSizeBytes) {
            onProgress(DownloadProgress(destination.length(), expectedSizeBytes))
            return
        }
        if (destination.length() > expectedSizeBytes) destination.delete()

        val resumeOffset = destination.takeIf(File::exists)?.length() ?: 0L
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "TPollScanner-Updater")
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
            if (declaredSize > 0L && initialSize + declaredSize != expectedSizeBytes) {
                throw DownloadException(
                    "O servidor informou um tamanho diferente do manifesto.",
                    retryable = false
                )
            }

            onProgress(DownloadProgress(initialSize, expectedSizeBytes))
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
                        onProgress(DownloadProgress(total, expectedSizeBytes))
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
        } finally {
            connection.disconnect()
        }
    }

    private fun validateApk(
        context: Context,
        apkFile: File,
        info: UpdateInfo
    ): ApkPreparationResult.Failed? {
        if (!apkFile.isFile || apkFile.length() != info.sizeBytes) {
            return ApkPreparationResult.Failed("O download do APK não foi concluído corretamente.")
        }
        if (!sha256(apkFile).equals(info.sha256, ignoreCase = true)) {
            return ApkPreparationResult.Failed(
                "Integridade do APK comprometida. O SHA-256 não corresponde."
            )
        }

        val packageManager = context.packageManager
        val archiveInfo = getArchivePackageInfo(packageManager, apkFile)
            ?: return ApkPreparationResult.Failed("O arquivo baixado não é um APK Android válido.")
        val installedInfo = getInstalledPackageInfo(packageManager, EXPECTED_PACKAGE_NAME)
            ?: return ApkPreparationResult.Failed("Não foi possível validar a instalação atual.")

        if (archiveInfo.packageName != EXPECTED_PACKAGE_NAME) {
            return ApkPreparationResult.Failed(
                "A atualização pertence a outro aplicativo e foi bloqueada."
            )
        }

        val archiveVersionCode = archiveInfo.versionCodeCompat()
        val installedVersionCode = installedInfo.versionCodeCompat()
        if (archiveVersionCode != info.versionCode.toLong()) {
            return ApkPreparationResult.Failed(
                "A versão do APK não corresponde à atualização anunciada."
            )
        }
        if (archiveVersionCode <= installedVersionCode) {
            return ApkPreparationResult.Failed("O APK baixado não é mais novo que o app instalado.")
        }

        val installedCertificates = signingCertificateDigests(installedInfo)
        val archiveCertificates = signingCertificateDigests(archiveInfo)
        if (installedCertificates.isEmpty() || archiveCertificates.isEmpty()) {
            return ApkPreparationResult.Failed(
                "Não foi possível confirmar a assinatura digital da atualização."
            )
        }
        if (EXPECTED_RELEASE_CERT_SHA256 !in installedCertificates) {
            return ApkPreparationResult.Failed(
                message = "Esta instalação não usa o certificado oficial atual. " +
                    "Ela precisa de uma migração manual única antes das próximas atualizações.",
                requiresOneTimeReinstall = true
            )
        }
        if (EXPECTED_RELEASE_CERT_SHA256 !in archiveCertificates) {
            return ApkPreparationResult.Failed(
                "A assinatura do APK não corresponde ao certificado oficial e foi bloqueada."
            )
        }
        if (installedCertificates.intersect(archiveCertificates).isEmpty()) {
            return ApkPreparationResult.Failed(
                "A assinatura do APK não é compatível com o aplicativo instalado."
            )
        }

        return null
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

    private fun updatesDirectory(context: Context): File =
        File(context.cacheDir, "updates").apply { mkdirs() }

    private fun preparedFile(context: Context, info: UpdateInfo): File =
        File(updatesDirectory(context), "TPollScanner-${info.versionName}-release.apk")

    private fun cleanupExcept(directory: File, keep: Set<File>) {
        directory.listFiles().orEmpty().filterNot(keep::contains).forEach(File::delete)
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

    private class DownloadException(message: String, val retryable: Boolean) : Exception(message)
}
