package com.tpoll.scanner.updater

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object UpdateScheduler {

    private const val PREFS_NAME = "update_prefs"
    private const val KEY_AUTOMATIC_UPDATES = "automatic_updates_enabled"
    private const val PERIODIC_CHECK_WORK_NAME = "tpoll_update_check"
    private const val IMMEDIATE_CHECK_WORK_NAME = "tpoll_update_check_now"
    private const val DOWNLOAD_WORK_PREFIX = "tpoll_update_download_"

    internal const val DATA_VERSION_CODE = "version_code"
    internal const val DATA_VERSION_NAME = "version_name"
    internal const val DATA_APK_URL = "apk_url"
    internal const val DATA_SHA256 = "sha256"
    internal const val DATA_SIZE_BYTES = "size_bytes"

    fun isAutomaticUpdatesEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTOMATIC_UPDATES, true)

    fun setAutomaticUpdatesEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTOMATIC_UPDATES, enabled)
            .apply()

        if (enabled) schedule(context) else cancel(context)
    }

    fun schedule(context: Context) {
        if (!isAutomaticUpdatesEnabled(context)) return

        val checkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodicRequest = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(checkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_CHECK_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )

        if (UpdateChecker.shouldCheck(context)) {
            val immediateRequest = OneTimeWorkRequestBuilder<UpdateWorker>()
                .setConstraints(checkConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_CHECK_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                immediateRequest
            )
        }
    }

    fun enqueueDownload(context: Context, info: UpdateInfo) {
        if (!isAutomaticUpdatesEnabled(context)) return

        val data = Data.Builder()
            .putInt(DATA_VERSION_CODE, info.version_code)
            .putString(DATA_VERSION_NAME, info.version_name)
            .putString(DATA_APK_URL, info.apk_url)
            .putString(DATA_SHA256, info.sha256)
            .putLong(DATA_SIZE_BYTES, info.size_bytes)
            .build()
        val downloadConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setInputData(data)
            .setConstraints(downloadConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .addTag(DOWNLOAD_WORK_PREFIX)
            .build()

        UpdateStateStore.write(
            context = context,
            phase = UpdatePhase.WAITING_FOR_WIFI,
            versionCode = info.version_code,
            versionName = info.version_name,
            message = "Aguardando uma conexão Wi-Fi adequada para baixar."
        )
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$DOWNLOAD_WORK_PREFIX${info.version_code}",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PERIODIC_CHECK_WORK_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_CHECK_WORK_NAME)
        workManager.cancelAllWorkByTag(DOWNLOAD_WORK_PREFIX)
    }
}
