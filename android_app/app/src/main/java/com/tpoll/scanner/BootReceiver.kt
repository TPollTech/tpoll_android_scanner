// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            val prefs = context.getSharedPreferences("scan_settings", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("auto_scan_enabled", true)

            if (enabled) {
                val useTimeSchedule = prefs.getBoolean("use_time_schedule", false)
                if (useTimeSchedule) {
                    scheduleAtTime(context)
                } else {
                    schedulePeriodicScan(context)
                }
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
            cancelAlarm(context)
        }

        fun scheduleAtTime(context: Context) {
            val prefs = context.getSharedPreferences("scan_settings", Context.MODE_PRIVATE)
            val hour = prefs.getInt("scheduled_hour", 3)
            val minute = prefs.getInt("scheduled_minute", 0)

            cancelAlarm(context)

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            val intent = Intent(context, ScanReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
                )
            }
        }

        private fun cancelAlarm(context: Context) {
            val intent = Intent(context, ScanReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
        }
    }
}
