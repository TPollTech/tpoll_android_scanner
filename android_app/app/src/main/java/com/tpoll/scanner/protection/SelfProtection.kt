// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.protection

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.tpoll.scanner.MainActivity
import com.tpoll.scanner.R
import com.tpoll.scanner.notifications.NotificationHelper

class SelfProtection(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val selfPackage = context.packageName
    private val handler = Handler(Looper.getMainLooper())
    private var monitorRunning = false

    companion object {
        private const val MONITOR_INTERVAL_MS = 5000L
        private const val NOTIFICATION_ID_PROTECTION = 3001
    }

    fun enableProtection() {
        startPeriodicMonitor()
        ensureComponentEnabled()
    }

    fun disableProtection() {
        monitorRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun startPeriodicMonitor() {
        if (monitorRunning) return
        monitorRunning = true

        val runnable = object : Runnable {
            override fun run() {
                if (!monitorRunning) return
                checkAndRestoreProtection()
                handler.postDelayed(this, MONITOR_INTERVAL_MS)
            }
        }
        handler.postDelayed(runnable, MONITOR_INTERVAL_MS)
    }

    private fun checkAndRestoreProtection() {
        try {
            ensureComponentEnabled()
            ensureBootReceiverEnabled()
            ensureServiceRunning()
            checkForDisabledComponents()
        } catch (e: Exception) {
            // Ignora erros no monitoramento
        }
    }

    private fun ensureComponentEnabled() {
        try {
            val componentName = ComponentName(selfPackage, "${selfPackage}.BootReceiver")
            val state = pm.getComponentEnabledSetting(componentName)

            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            ) {
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) { }
    }

    private fun ensureBootReceiverEnabled() {
        try {
            val componentName = ComponentName(selfPackage, "${selfPackage}.BootReceiver")
            val state = pm.getComponentEnabledSetting(componentName)

            if (state != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) { }
    }

    private fun ensureServiceRunning() {
        try {
            if (!ShieldService.isRunning()) {
                val intent = Intent(context, ShieldService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        } catch (e: Exception) { }
    }

    private fun checkForDisabledComponents() {
        val criticalComponents = listOf(
            "${selfPackage}.BootReceiver",
            "${selfPackage}.ScanReceiver",
            "${selfPackage}.ScanService",
            "${selfPackage}.protection.ShieldService"
        )

        for (componentName in criticalComponents) {
            try {
                val component = ComponentName(selfPackage, componentName)
                val state = pm.getComponentEnabledSetting(component)

                if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                    state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                ) {
                    pm.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    showProtectionNotification("Componente $componentName foi restaurado")
                }
            } catch (e: Exception) { }
        }
    }

    private fun showProtectionNotification(message: String) {
        try {
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_SCAN)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle("TPoll Scanner - Auto-proteção")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(NOTIFICATION_ID_PROTECTION, notification)
        } catch (e: Exception) { }
    }

    fun requestDisableDeviceAdmin() {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = ComponentName(selfPackage, "${selfPackage}.protection.DeviceAdminReceiver")

            if (isAdminActive(dpm, componentName)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "TPoll Scanner precisa de administrador do dispositivo para se proteger de malware"
                    )
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) { }
    }

    fun isDeviceAdminActive(): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = ComponentName(selfPackage, "${selfPackage}.protection.DeviceAdminReceiver")
            isAdminActive(dpm, componentName)
        } catch (e: Exception) { false }
    }

    private fun isAdminActive(dpm: DevicePolicyManager, componentName: ComponentName): Boolean {
        val activeAdmins = dpm.activeAdmins ?: return false
        return activeAdmins.any { it == componentName }
    }

    fun protectAgainstUninstall() {
        try {
            if (!isDeviceAdminActive()) {
                requestDisableDeviceAdmin()
            }
        } catch (e: Exception) { }
    }

    fun isProtectionActive(): Boolean {
        return monitorRunning && isComponentEnabled()
    }

    private fun isComponentEnabled(): Boolean {
        return try {
            val componentName = ComponentName(selfPackage, "${selfPackage}.BootReceiver")
            val state = pm.getComponentEnabledSetting(componentName)
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        } catch (e: Exception) { false }
    }
}