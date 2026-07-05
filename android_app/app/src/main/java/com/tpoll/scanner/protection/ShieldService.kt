package com.tpoll.scanner.protection

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.tpoll.scanner.MainActivity
import com.tpoll.scanner.R
import com.tpoll.scanner.notifications.NotificationHelper
import kotlinx.coroutines.*

class ShieldService : Service() {

    companion object {
        const val ACTION_START = "com.tpoll.scanner.protection.START"
        const val ACTION_STOP = "com.tpoll.scanner.protection.STOP"
        const val ACTION_SCAN_NOW = "com.tpoll.scanner.protection.SCAN_NOW"
        private const val NOTIFICATION_ID = 3001

        private var isRunning = false
        private var currentThreatCount = 0

        fun isRunning(): Boolean = isRunning

        fun start(context: Context) {
            val intent = Intent(context, ShieldService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ShieldService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun getCurrentThreatCount(): Int = currentThreatCount
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var periodicJob: Job? = null
    private lateinit var detector: ShieldDetector
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        detector = ShieldDetector(this)
        acquireWakeLock()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = buildNotification("Proteção em tempo real ativa")
                startForeground(NOTIFICATION_ID, notification)
                startPeriodicChecks()
            }
            ACTION_STOP -> {
                stopPeriodicChecks()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SCAN_NOW -> {
                scope.launch { scanNow() }
            }
        }
        return START_STICKY
    }

    private fun startPeriodicChecks() {
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (isActive) {
                scanNow()
                delay(30_000L)
            }
        }
    }

    private fun stopPeriodicChecks() {
        periodicJob?.cancel()
        periodicJob = null
    }

    private suspend fun scanNow() {
        withContext(Dispatchers.IO) {
            try {
                val status = detector.detectAllThreats()
                currentThreatCount = status.totalThreats

                val title = if (currentThreatCount > 0) {
                    "$currentThreatCount ameaça(s) detectada(s) - Toque para ver"
                } else {
                    "Proteção em tempo real ativa - Nenhuma ameaça"
                }
                val notification = buildNotification(title)
                val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(NOTIFICATION_ID, notification)

                if (status.hasCriticalThreats) {
                    val app = application as com.tpoll.scanner.TPollApp
                    for (threat in status.overlayApps) {
                        app.notificationHelper.showShieldAlert(threat)
                    }
                    for (threat in status.accessibilityAbusers) {
                        app.notificationHelper.showShieldAlert(threat)
                    }
                    for (threat in status.deviceAdmins) {
                        app.notificationHelper.showShieldAlert(threat)
                    }
                }

                val prefs = getSharedPreferences("protection_status", Context.MODE_PRIVATE)
                prefs.edit()
                    .putInt("overlay_count", status.overlayApps.size)
                    .putInt("accessibility_count", status.accessibilityAbusers.size)
                    .putInt("device_admin_count", status.deviceAdmins.size)
                    .putInt("notification_listener_count", status.notificationListeners.size)
                    .putInt("installer_count", status.installerApps.size)
                    .putInt("usage_stats_count", status.usageStatsAbusers.size)
                    .putLong("last_check", status.lastChecked)
                    .putBoolean("real_time_active", true)
                    .apply()
            } catch (e: Exception) { }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        scope.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_PROTECTION)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("TPoll Shield")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "tpoll::shield_lock"
            ).apply {
                acquire(10 * 60 * 1000L)
            }
        } catch (e: Exception) { }
    }
}
