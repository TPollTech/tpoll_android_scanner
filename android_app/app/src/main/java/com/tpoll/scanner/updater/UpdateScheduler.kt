package com.tpoll.scanner.updater

import android.content.Context
import androidx.work.Constraints
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
    private const val PERIODIC_WORK_NAME = "tpoll_automatic_update"
    private const val IMMEDIATE_WORK_NAME = "tpoll_automatic_update_now"

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

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()
        val periodicRequest = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )

        if (UpdateChecker.shouldCheck(context)) {
            val immediateRequest = OneTimeWorkRequestBuilder<UpdateWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                immediateRequest
            )
        }
    }

    private fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE_WORK_NAME)
    }
}
