// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.protection

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.tpoll.scanner.MainActivity
import com.tpoll.scanner.R
import com.tpoll.scanner.notifications.NotificationHelper

class DeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val NOTIFICATION_ID_ADMIN = 3002
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        showProtectionNotification(context, "TPoll Scanner agora está protegido contra desinstalação")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        showProtectionNotification(context, "TPoll Scanner perdeu a proteção contra desinstalação")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Desativar a proteção do TPoll Scanner deixará o app vulnerável a malware. Tem certeza?"
    }

    private fun showProtectionNotification(context: Context, message: String) {
        try {
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_SCAN)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle("TPoll Scanner - Device Admin")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(NOTIFICATION_ID_ADMIN, notification)
        } catch (e: Exception) { }
    }
}