package com.tpoll.scanner.updater

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tpoll.scanner.notifications.NotificationHelper

class UpdateDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!UpdateScheduler.isAutomaticUpdatesEnabled(applicationContext)) {
            return Result.success()
        }

        val info = UpdateInfo(
            versionCode = inputData.getInt(UpdateScheduler.DATA_VERSION_CODE, 0),
            versionName = inputData.getString(UpdateScheduler.DATA_VERSION_NAME).orEmpty(),
            apkUrl = inputData.getString(UpdateScheduler.DATA_APK_URL).orEmpty(),
            sha256 = inputData.getString(UpdateScheduler.DATA_SHA256).orEmpty(),
            sizeBytes = inputData.getLong(UpdateScheduler.DATA_SIZE_BYTES, 0L),
            downloadUrl = inputData.getString(UpdateScheduler.DATA_DOWNLOAD_URL).orEmpty(),
            releaseNotes = inputData.getString(UpdateScheduler.DATA_RELEASE_NOTES)
                .orEmpty()
                .lineSequence()
                .filter(String::isNotBlank)
                .toList(),
            mandatory = inputData.getBoolean(UpdateScheduler.DATA_MANDATORY, false),
            minVersionCode = inputData.getInt(UpdateScheduler.DATA_MIN_VERSION_CODE, 1)
        )
        UpdateManifestValidator.error(info)?.let { message ->
            UpdateStateStore.write(
                context = applicationContext,
                phase = UpdatePhase.FAILED,
                message = message
            )
            return Result.failure()
        }

        val notificationHelper = NotificationHelper(applicationContext)
        UpdateStateStore.write(
            context = applicationContext,
            phase = UpdatePhase.DOWNLOADING,
            versionCode = info.versionCode,
            versionName = info.versionName,
            totalBytes = info.sizeBytes
        )

        var lastPersistedPercent = -1
        return when (
            val preparation = ApkInstaller.downloadAndValidate(
                context = applicationContext,
                info = info,
                onProgress = { progress ->
                    val percent = (progress.fraction * 100f).toInt().coerceIn(0, 100)
                    setProgress(
                        workDataOf(
                            PROGRESS_PERCENT to percent,
                            PROGRESS_DOWNLOADED_BYTES to progress.downloadedBytes,
                            PROGRESS_TOTAL_BYTES to progress.totalBytes
                        )
                    )
                    if (percent != lastPersistedPercent) {
                        lastPersistedPercent = percent
                        UpdateStateStore.write(
                            context = applicationContext,
                            phase = UpdatePhase.DOWNLOADING,
                            versionCode = info.versionCode,
                            versionName = info.versionName,
                            downloadedBytes = progress.downloadedBytes,
                            totalBytes = progress.totalBytes
                        )
                    }
                }
            )
        ) {
            is ApkPreparationResult.Ready -> {
                if (!ApkInstaller.canRequestPackageInstalls(applicationContext)) {
                    UpdateStateStore.write(
                        context = applicationContext,
                        phase = UpdatePhase.PERMISSION_REQUIRED,
                        versionCode = info.versionCode,
                        versionName = info.versionName,
                        message = "Autorize esta fonte para continuar a instalação."
                    )
                    notificationHelper.showUpdatePermissionRequired(info.versionName)
                } else {
                    val installIntent = ApkInstaller.createInstallerIntent(applicationContext, info)
                    if (installIntent == null) {
                        val message = "O APK validado não está mais disponível."
                        UpdateStateStore.write(
                            context = applicationContext,
                            phase = UpdatePhase.FAILED,
                            versionCode = info.versionCode,
                            versionName = info.versionName,
                            message = message
                        )
                        notificationHelper.showUpdateInstallFailed(message)
                        return Result.failure()
                    }
                    UpdateStateStore.write(
                        context = applicationContext,
                        phase = UpdatePhase.READY_TO_INSTALL,
                        versionCode = info.versionCode,
                        versionName = info.versionName,
                        message = "Download validado. Toque para abrir o instalador do Android."
                    )
                    notificationHelper.showUpdateReadyToInstall(installIntent)
                }
                Result.success()
            }

            is ApkPreparationResult.Failed -> {
                if (preparation.retryable && runAttemptCount < MAX_WORKER_RETRIES) {
                    Result.retry()
                } else {
                    UpdateStateStore.write(
                        context = applicationContext,
                        phase = UpdatePhase.FAILED,
                        versionCode = info.versionCode,
                        versionName = info.versionName,
                        message = preparation.message
                    )
                    notificationHelper.showUpdateInstallFailed(preparation.message)
                    Result.success()
                }
            }
        }
    }

    companion object {
        const val PROGRESS_PERCENT = "progress_percent"
        const val PROGRESS_DOWNLOADED_BYTES = "progress_downloaded_bytes"
        const val PROGRESS_TOTAL_BYTES = "progress_total_bytes"
        private const val MAX_WORKER_RETRIES = 3
    }
}
