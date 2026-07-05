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
import com.tpoll.scanner.data.AppDatabase
import com.tpoll.scanner.model.AppFinding
import com.tpoll.scanner.model.QuarantinedApp
import com.tpoll.scanner.model.RiskLevel
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
            val intent = Intent(context, ShieldService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ShieldService::class.java).apply { action = ACTION_STOP }
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

                saveThreatsToDatabase(status)

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
                    for (threat in status.threats.filter { it.severity >= 70 }) {
                        app.notificationHelper.showShieldAlert(threat)
                        autoRemoveThreat(threat)
                    }
                }

                val prefs = getSharedPreferences("protection_status", Context.MODE_PRIVATE)
                prefs.edit()
                    .putInt("threat_count", status.totalThreats)
                    .putInt("malware_count", status.threats.count { it.isMalware })
                    .putInt("critical_count", status.threats.count { it.severity >= 70 })
                    .putLong("last_check", status.lastChecked)
                    .putBoolean("real_time_active", true)
                    .apply()
            } catch (_: Exception) { }
        }
    }

    private suspend fun saveThreatsToDatabase(status: ProtectionStatus) {
        try {
            val db = AppDatabase.getInstance(this)
            val findings = status.threats.map { threat ->
                AppFinding(
                    packageName = threat.packageName,
                    appName = threat.appName,
                    score = threat.severity,
                    level = when {
                        threat.severity >= 80 -> RiskLevel.HIGH
                        threat.severity >= 50 -> RiskLevel.MEDIUM
                        else -> RiskLevel.LOW
                    },
                    reasons = listOf(threat.details),
                    isKnownThreat = threat.isMalware,
                    threatType = threat.type.name,
                    timestamp = System.currentTimeMillis()
                )
            }
            db.appDao().insertAll(findings)
        } catch (_: Exception) { }
    }

    private suspend fun autoRemoveThreat(threat: com.tpoll.scanner.protection.ShieldThreat) {
        try {
            val settings = getSharedPreferences("scan_settings", Context.MODE_PRIVATE)
            val autoHigh = settings.getBoolean("auto_remove_high", true)
            val autoMedium = settings.getBoolean("auto_remove_medium", false)
            val shouldRemove = (threat.severity >= 80 && autoHigh) || (threat.severity in 50..79 && autoMedium)
            if (!shouldRemove) return

            val db = AppDatabase.getInstance(this)
            val appName = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(threat.packageName, 0)).toString()
            } catch (_: Exception) { threat.appName }

            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${threat.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try { startActivity(intent) } catch (_: Exception) { }

            val quarantined = QuarantinedApp(
                packageName = threat.packageName,
                appName = appName,
                reason = threat.details,
                riskLevel = if (threat.severity >= 80) "HIGH" else "MEDIUM",
                score = threat.severity,
                removedBy = "auto"
            )
            db.quarantineDao().insert(quarantined)
        } catch (_: Exception) { }
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
        } catch (_: Exception) { }
    }
}
