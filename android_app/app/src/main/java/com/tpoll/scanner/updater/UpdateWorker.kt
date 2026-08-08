package com.tpoll.scanner.updater

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tpoll.scanner.notifications.NotificationHelper

class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!UpdateScheduler.isAutomaticUpdatesEnabled(applicationContext)) {
            return Result.success()
        }
        if (!UpdateChecker.shouldCheck(applicationContext)) {
            return Result.success()
        }

        UpdateChecker.init(applicationContext)
        return when (val update = UpdateChecker().checkForUpdatesWithRetry(applicationContext)) {
            is UpdateResult.Available -> install(update.info)
            is UpdateResult.UpToDate -> Result.success()
            is UpdateResult.Error -> if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun install(info: UpdateInfo): Result {
        val notificationHelper = NotificationHelper(applicationContext)
        if (!ApkInstaller.canRequestPackageInstalls(applicationContext)) {
            notificationHelper.showUpdatePermissionRequired(info.version_name)
            return Result.success()
        }

        return when (
            val install = ApkInstaller.downloadAndInstall(
                context = applicationContext,
                apkUrl = info.apk_url.ifEmpty { info.download_url },
                expectedVersionCode = info.version_code
            )
        ) {
            is ApkInstallRequestResult.Submitted -> Result.success()
            is ApkInstallRequestResult.PermissionRequired -> {
                notificationHelper.showUpdatePermissionRequired(info.version_name)
                Result.success()
            }
            is ApkInstallRequestResult.Failed -> {
                notificationHelper.showUpdateInstallFailed(install.message)
                if (runAttemptCount < 2 && !install.requiresOneTimeReinstall) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }
}
