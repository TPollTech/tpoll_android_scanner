// Copyright (c) 2026 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.webguard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings

object WebProtectionToggle {

    const val VPN_REQUEST_CODE = 5001

    fun startVPN(context: Context, activity: Activity? = null): Boolean {
        val intent = VpnService.prepare(context)
        if (intent != null && activity != null) {
            activity.startActivityForResult(intent, VPN_REQUEST_CODE)
            return false
        }

        WebProtectionConfig.getInstance(context).setVPNEnabled(true)
        WebBlockerVPNService.start(context)
        return true
    }

    fun startVPNWithPermissionCheck(context: Context): Boolean {
        val intent = VpnService.prepare(context)
        if (intent != null) {
            return false
        }

        WebProtectionConfig.getInstance(context).setVPNEnabled(true)
        WebBlockerVPNService.start(context)
        return true
    }

    fun stopVPN(context: Context) {
        WebProtectionConfig.getInstance(context).setVPNEnabled(false)
        WebBlockerVPNService.stop(context)
    }

    fun toggleVPN(context: Context, activity: Activity? = null): Boolean {
        val config = WebProtectionConfig.getInstance(context)
        return if (config.isVPNEnabled()) {
            stopVPN(context)
            false
        } else {
            startVPN(context, activity)
        }
    }

    fun startAccessibility(context: Context) {
        WebProtectionConfig.getInstance(context).setAccessibilityEnabled(true)

        if (!URLMonitorService.isAccessibilityEnabled(context)) {
            URLMonitorService.openAccessibilitySettings(context)
        }
    }

    fun stopAccessibility(context: Context) {
        WebProtectionConfig.getInstance(context).setAccessibilityEnabled(false)
    }

    fun toggleAccessibility(context: Context): Boolean {
        val config = WebProtectionConfig.getInstance(context)
        return if (config.isAccessibilityEnabled()) {
            stopAccessibility(context)
            false
        } else {
            startAccessibility(context)
            true
        }
    }

    fun isOtherVPNActive(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
            return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
        }
        return false
    }

    fun startAll(context: Context, activity: Activity? = null) {
        startVPN(context, activity)
        startAccessibility(context)
    }

    fun stopAll(context: Context) {
        stopVPN(context)
        stopAccessibility(context)
    }

    fun getProtectionStatus(context: Context): WebProtectionStatus {
        val config = WebProtectionConfig.getInstance(context)
        val vpnEnabled = config.isVPNEnabled()
        val accessibilityEnabled = config.isAccessibilityEnabled()
        val vpnRunning = WebBlockerVPNService.isRunning()
        val accessibilityRunning = URLMonitorService.isMonitoring()
        val otherVPNActive = isOtherVPNActive(context)

        return WebProtectionStatus(
            vpnEnabled = vpnEnabled,
            vpnRunning = vpnRunning,
            accessibilityEnabled = accessibilityEnabled,
            accessibilityRunning = accessibilityRunning,
            otherVPNActive = otherVPNActive,
            blockedToday = config.getBlockedToday(),
            totalBlocked = WebBlockerVPNService.getBlockedCount() + URLMonitorService.getBlockedToday()
        )
    }

    fun onVPNPermissionResult(context: Context, resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            WebProtectionConfig.getInstance(context).setVPNEnabled(true)
            WebBlockerVPNService.start(context)
        }
    }
}

data class WebProtectionStatus(
    val vpnEnabled: Boolean,
    val vpnRunning: Boolean,
    val accessibilityEnabled: Boolean,
    val accessibilityRunning: Boolean,
    val otherVPNActive: Boolean,
    val blockedToday: Int,
    val totalBlocked: Int
) {
    fun isActive(): Boolean = vpnRunning || accessibilityRunning

    fun getSummary(context: Context): String {
        return when {
            !vpnEnabled && !accessibilityEnabled -> "Proteção desativada"
            otherVPNActive -> "Outra VPN ativa - WebGuard aguardando"
            vpnRunning && accessibilityRunning -> "VPN + Monitoramento ativos"
            vpnRunning -> "VPN ativa"
            accessibilityRunning -> "Monitoramento ativo"
            vpnEnabled -> "VPN ativando..."
            accessibilityEnabled -> "Monitoramento ativando..."
            else -> "Desativado"
        }
    }

    fun getStatusColor(context: Context): Int {
        return when {
            isActive() -> 0xFF4CAF50.toInt()
            vpnEnabled || accessibilityEnabled -> 0xFFFFA000.toInt()
            else -> 0xFF9E9E9E.toInt()
        }
    }
}
