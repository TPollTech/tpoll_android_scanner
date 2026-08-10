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
        val versionCode = intent.getIntExtra(ApkInstaller.EXTRA_VERSION_CODE, 0)
        val versionName = intent.getStringExtra(ApkInstaller.EXTRA_VERSION_NAME).orEmpty()
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_SUCCESS -> {
                UpdateStateStore.write(
                    context = context,
                    phase = UpdatePhase.INSTALLED,
                    versionCode = versionCode,
                    versionName = versionName,
                    message = "Atualização instalada com sucesso."
                )
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                UpdateStateStore.write(
                    context = context,
                    phase = UpdatePhase.CONFIRMATION_REQUIRED,
                    versionCode = versionCode,
                    versionName = versionName,
                    message = "O Android precisa confirmar a instalação."
                )
                confirmationIntent(intent)?.let(notificationHelper::showUpdateReadyToInstall)
                    ?: run {
                        val message =
                        "O Android pediu confirmação, mas não forneceu a tela de instalação."
                        UpdateStateStore.write(
                            context = context,
                            phase = UpdatePhase.FAILED,
                            versionCode = versionCode,
                            versionName = versionName,
                            message = message
                        )
                        notificationHelper.showUpdateInstallFailed(message)
                    }
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: "Falha desconhecida do instalador Android."
                UpdateStateStore.write(
                    context = context,
                    phase = UpdatePhase.FAILED,
                    versionCode = versionCode,
                    versionName = versionName,
                    message = message
                )
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
