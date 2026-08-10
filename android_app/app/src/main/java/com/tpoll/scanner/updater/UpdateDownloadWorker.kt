package com.tpoll.scanner.updater

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tpoll.scanner.notifications.NotificationHelper

class UpdateDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!UpdateScheduler.isAutomaticUpdatesEnabled(applicationContext)) {
            return Result.success()
        }

        val versionCode = inputData.getInt(UpdateScheduler.DATA_VERSION_CODE, 0)
        val versionName = inputData.getString(UpdateScheduler.DATA_VERSION_NAME).orEmpty()
        val apkUrl = inputData.getString(UpdateScheduler.DATA_APK_URL).orEmpty()
        val sha256 = inputData.getString(UpdateScheduler.DATA_SHA256).orEmpty()
        val sizeBytes = inputData.getLong(UpdateScheduler.DATA_SIZE_BYTES, 0L)
        if (versionCode <= 0 || versionName.isBlank() || apkUrl.isBlank()) {
            return Result.failure()
        }

        val notificationHelper = NotificationHelper(applicationContext)
        if (!ApkInstaller.canRequestPackageInstalls(applicationContext)) {
            UpdateStateStore.write(
                context = applicationContext,
                phase = UpdatePhase.PERMISSION_REQUIRED,
                versionCode = versionCode,
                versionName = versionName,
                message = "Permissão necessária para instalar a atualização."
            )
            notificationHelper.showUpdatePermissionRequired(versionName)
            return Result.success()
        }

        UpdateStateStore.write(
            context = applicationContext,
            phase = UpdatePhase.DOWNLOADING,
            versionCode = versionCode,
            versionName = versionName
        )
        return when (
            val install = ApkInstaller.downloadAndInstall(
                context = applicationContext,
                apkUrl = apkUrl,
                expectedVersionCode = versionCode,
                expectedSha256 = sha256,
                expectedSizeBytes = sizeBytes
            )
        ) {
            is ApkInstallRequestResult.Submitted -> {
                UpdateStateStore.write(
                    context = applicationContext,
                    phase = UpdatePhase.INSTALL_SUBMITTED,
                    versionCode = versionCode,
                    versionName = versionName
                )
                Result.success()
            }

            is ApkInstallRequestResult.PermissionRequired -> {
                UpdateStateStore.write(
                    context = applicationContext,
                    phase = UpdatePhase.PERMISSION_REQUIRED,
                    versionCode = versionCode,
                    versionName = versionName,
                    message = "Permissão necessária para instalar a atualização."
                )
                notificationHelper.showUpdatePermissionRequired(versionName)
                Result.success()
            }

            is ApkInstallRequestResult.Failed -> {
                if (install.retryable && runAttemptCount < MAX_WORKER_RETRIES) {
                    Result.retry()
                } else {
                    UpdateStateStore.write(
                        context = applicationContext,
                        phase = UpdatePhase.FAILED,
                        versionCode = versionCode,
                        versionName = versionName,
                        message = install.message
                    )
                    notificationHelper.showUpdateInstallFailed(install.message)
                    Result.success()
                }
            }
        }
    }

    companion object {
        private const val MAX_WORKER_RETRIES = 3
    }
}
