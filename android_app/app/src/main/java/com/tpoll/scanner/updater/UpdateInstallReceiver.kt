package com.tpoll.scanner.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.tpoll.scanner.notifications.NotificationHelper

class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ApkInstaller.ACTION_INSTALL_STATUS) return

        val notificationHelper = NotificationHelper(context)
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                confirmationIntent(intent)?.let(notificationHelper::showUpdateReadyToInstall)
                    ?: notificationHelper.showUpdateInstallFailed(
                        "O Android pediu confirmação, mas não forneceu a tela de instalação."
                    )
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: "Falha desconhecida do instalador Android."
                notificationHelper.showUpdateInstallFailed(message)
            }
        }
    }

    private fun confirmationIntent(statusIntent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            statusIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            statusIntent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
}
