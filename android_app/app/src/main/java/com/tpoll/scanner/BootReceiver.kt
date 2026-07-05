package com.tpoll.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            val prefs = context.getSharedPreferences("scan_settings", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("auto_scan_enabled", true)

            if (enabled) {
                schedulePeriodicScan(context)
            }
        }
    }

    companion object {
        fun schedulePeriodicScan(context: Context) {
            val prefs = context.getSharedPreferences("scan_settings", Context.MODE_PRIVATE)
            val intervalHours = prefs.getInt("scan_interval_hours", 6)

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ScanWorker>(
                intervalHours.toLong(), TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "tpoll_periodic_scan",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun cancelPeriodicScan(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("tpoll_periodic_scan")
        }
    }
}
