package com.tpoll.scanner

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

class ScanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ScanService.startScan(context)

        val prefs = context.getSharedPreferences("scan_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("use_time_schedule", false)) {
            rescheduleNextDay(context)
        }
    }

    private fun rescheduleNextDay(context: Context) {
        val prefs = context.getSharedPreferences("scan_settings", Context.MODE_PRIVATE)
        val hour = prefs.getInt("scheduled_hour", 3)
        val minute = prefs.getInt("scheduled_minute", 0)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
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
        }
    }
}
