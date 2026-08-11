package com.tpoll.scanner.updater

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

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

        UpdateStateStore.write(applicationContext, UpdatePhase.CHECKING)
        return when (val update = UpdateChecker(applicationContext).checkForUpdates()) {
            is UpdateResult.Available -> {
                UpdateStateStore.write(
                    context = applicationContext,
                    phase = UpdatePhase.AVAILABLE,
                    versionCode = update.info.versionCode,
                    versionName = update.info.versionName
                )
                UpdateScheduler.enqueueDownload(applicationContext, update.info)
                Result.success()
            }

            is UpdateResult.UpToDate -> {
                UpdateStateStore.write(
                    context = applicationContext,
                    phase = UpdatePhase.IDLE,
                    versionCode = update.installedVersion.code.toInt(),
                    versionName = update.installedVersion.name
                )
                Result.success()
            }

            is UpdateResult.Error -> {
                if (runAttemptCount < MAX_WORKER_RETRIES) {
                    Result.retry()
                } else {
                    UpdateStateStore.write(
                        context = applicationContext,
                        phase = UpdatePhase.FAILED,
                        message = update.message
                    )
                    Result.success()
                }
            }
        }
    }

    companion object {
        private const val MAX_WORKER_RETRIES = 3
    }
}
