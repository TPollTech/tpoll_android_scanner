package com.tpoll.scanner.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tpoll.scanner.MainActivity
import com.tpoll.scanner.R
import com.tpoll.scanner.model.AppFinding
import com.tpoll.scanner.model.RiskLevel
import com.tpoll.scanner.protection.ShieldThreat

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_SCAN = "scan_channel"
        const val CHANNEL_THREATS = "threats_channel"
        const val CHANNEL_PROTECTION = "protection_channel"
        const val NOTIFICATION_SCAN_ID = 1001
        const val NOTIFICATION_THREATS_ID = 1002
        const val NOTIFICATION_SHIELD_ALERT_BASE = 2000
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val scanChannel = NotificationChannel(
            CHANNEL_SCAN,
            context.getString(R.string.shield_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Varredura em andamento"
            setShowBadge(false)
        }

        val threatsChannel = NotificationChannel(
            CHANNEL_THREATS,
            context.getString(R.string.threats_notification_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Ameaças detectadas e removidas"
            enableVibration(true)
        }

        val protectionChannel = NotificationChannel(
            CHANNEL_PROTECTION,
            "Proteção em tempo real",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notificação persistente do Shield de proteção"
            setShowBadge(false)
        }

        manager.createNotificationChannel(scanChannel)
        manager.createNotificationChannel(threatsChannel)
        manager.createNotificationChannel(protectionChannel)
    }

    fun showScanProgress(current: Int, total: Int, packageName: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SCAN)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Escaneando apps...")
            .setContentText("$current/$total: $packageName")
            .setProgress(total, current, false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()

        manager.notify(NOTIFICATION_SCAN_ID, notification)
    }

    fun showScanComplete(
        totalScanned: Int,
        highRisk: Int,
        mediumRisk: Int,
        removedCount: Int
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_SCAN_ID)

        if (highRisk == 0 && mediumRisk == 0) {
            return
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (removedCount > 0) {
            "Ameaças removidas: $removedCount"
        } else {
            "Ameaças detectadas: ${highRisk + mediumRisk}"
        }

        val text = buildString {
            append("$totalScanned apps analisados. ")
            if (highRisk > 0) append("$highRisk alto risco. ")
            if (mediumRisk > 0) append("$mediumRisk médio risco. ")
            if (removedCount > 0) append("$removedCount removidos automaticamente.")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_THREATS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_THREATS_ID, notification)
    }

    fun showThreatRemoved(finding: AppFinding) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val riskLabel = when (finding.level) {
            RiskLevel.HIGH -> "ALTO"
            RiskLevel.MEDIUM -> "MÉDIO"
            RiskLevel.LOW -> "BAIXO"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_THREATS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Ameaça removida: ${finding.appName}")
            .setContentText("Risco $riskLabel (${finding.score}/100) - ${finding.reasons.firstOrNull() ?: ""}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(finding.packageName.hashCode(), notification)
    }

    fun showShieldAlert(threat: ShieldThreat) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_THREATS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("ALERTA: ${threat.appName}")
            .setContentText(threat.details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(threat.details))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_SHIELD_ALERT_BASE + threat.packageName.hashCode(), notification)
    }

    fun showUpdateAvailable(versionName: String, changelog: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_THREATS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Nova versão disponível: $versionName")
            .setContentText("Toque para ver as novidades")
            .setStyle(NotificationCompat.BigTextStyle().bigText(changelog.take(500)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(4001, notification)
    }

    fun cancelAll() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
    }
}
