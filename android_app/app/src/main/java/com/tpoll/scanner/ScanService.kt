package com.tpoll.scanner

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.tpoll.scanner.model.AppFinding
import com.tpoll.scanner.model.RiskLevel
import com.tpoll.scanner.model.ScanResult
import com.tpoll.scanner.notifications.NotificationHelper
import com.tpoll.scanner.remover.AppRemover
import com.tpoll.scanner.scanner.AppAnalyzer
import kotlinx.coroutines.*

class ScanService : Service() {

    companion object {
        const val ACTION_START_SCAN = "com.tpoll.scanner.START_SCAN"
        const val ACTION_STOP_SCAN = "com.tpoll.scanner.STOP_SCAN"
        private const val NOTIFICATION_ID = 2001

        private var scanJob: Job? = null
        private var isScanning = false

        fun startScan(context: Context) {
            val intent = Intent(context, ScanService::class.java).apply {
                action = ACTION_START_SCAN
            }
            context.startForegroundService(intent)
        }

        fun stopScan(context: Context) {
            val intent = Intent(context, ScanService::class.java).apply {
                action = ACTION_STOP_SCAN
            }
            context.startService(intent)
        }

        fun isScanRunning(): Boolean = isScanning
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SCAN -> {
                startForeground(NOTIFICATION_ID, buildNotification("Preparando scan..."))
                startScan()
            }
            ACTION_STOP_SCAN -> {
                stopScan()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startScan() {
        if (isScanning) return
        isScanning = true

        scanJob = scope.launch {
            try {
                val analyzer = AppAnalyzer(applicationContext)
                val remover = AppRemover(applicationContext)
                val app = application as TPollApp

                val findings = analyzer.analyzeAllPackages(
                    thirdPartyOnly = true
                ) { current, total, packageName ->
                    if (isActive) {
                        withContext(Dispatchers.Main) {
                            updateNotification("Escaneando $current/$total: $packageName")
                            app.notificationHelper.showScanProgress(current, total, packageName)
                        }
                    }
                }

                val removable = remover.getRemovableApps(findings)
                var removedCount = 0

                for (finding in removable) {
                    if (!isActive) break
                    val result = remover.removeApp(finding)
                    if (result.success) {
                        removedCount++
                        withContext(Dispatchers.Main) {
                            app.notificationHelper.showThreatRemoved(finding)
                        }
                    }
                }

                val highRisk = findings.count { it.level == RiskLevel.HIGH }
                val mediumRisk = findings.count { it.level == RiskLevel.MEDIUM }

                withContext(Dispatchers.Main) {
                    app.notificationHelper.showScanComplete(
                        totalScanned = findings.size,
                        highRisk = highRisk,
                        mediumRisk = mediumRisk,
                        removedCount = removedCount
                    )
                }

                saveScanResult(findings, removedCount)

            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                isScanning = false
                withContext(Dispatchers.Main) {
                    stopSelf()
                }
            }
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        isScanning = false
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_SCAN)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("TPoll Scanner")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun saveScanResult(findings: List<AppFinding>, removedCount: Int) {
        val prefs = getSharedPreferences("scan_results", MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putLong("last_scan_time", System.currentTimeMillis())
        editor.putInt("last_scan_total", findings.size)
        editor.putInt("last_scan_high", findings.count { it.level == RiskLevel.HIGH })
        editor.putInt("last_scan_medium", findings.count { it.level == RiskLevel.MEDIUM })
        editor.putInt("last_scan_removed", removedCount)

        val history = prefs.getString("scan_history", "[]") ?: "[]"
        try {
            val jsonArray = org.json.JSONArray(history)
            val entry = org.json.JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("total", findings.size)
                put("high", findings.count { it.level == RiskLevel.HIGH })
                put("medium", findings.count { it.level == RiskLevel.MEDIUM })
                put("removed", removedCount)
            }
            jsonArray.put(entry)

            while (jsonArray.length() > 50) {
                jsonArray.remove(0)
            }

            editor.putString("scan_history", jsonArray.toString())
        } catch (e: Exception) {
            editor.putString("scan_history", "[]")
        }

        editor.apply()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "tpoll::scan_lock"
        ).apply {
            acquire(30 * 60 * 1000L) // 30 minutos max
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }
}
