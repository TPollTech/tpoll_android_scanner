package com.tpoll.scanner.protection

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

data class ShieldThreat(
    val type: ShieldThreatType,
    val packageName: String,
    val appName: String,
    val details: String,
    val severity: Int
)

enum class ShieldThreatType {
    OVERLAY_APP,
    ACCESSIBILITY_ABUSE,
    DEVICE_ADMIN_ABUSE,
    NOTIFICATION_LISTENER,
    INSTALLER_APP,
    USAGE_STATS_ABUSE,
    SUSPENDED_PACKAGE
}

data class ProtectionStatus(
    val overlayApps: List<ShieldThreat> = emptyList(),
    val accessibilityAbusers: List<ShieldThreat> = emptyList(),
    val deviceAdmins: List<ShieldThreat> = emptyList(),
    val notificationListeners: List<ShieldThreat> = emptyList(),
    val installerApps: List<ShieldThreat> = emptyList(),
    val usageStatsAbusers: List<ShieldThreat> = emptyList(),
    val isRealTimeProtectionActive: Boolean = false,
    val lastChecked: Long = 0L
) {
    val totalThreats: Int get() =
        overlayApps.size + accessibilityAbusers.size + deviceAdmins.size +
        notificationListeners.size + installerApps.size + usageStatsAbusers.size

    val hasCriticalThreats: Boolean get() =
        overlayApps.isNotEmpty() || accessibilityAbusers.isNotEmpty() || deviceAdmins.isNotEmpty()
}

class ShieldDetector(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val selfPackage = context.packageName

    fun detectAllThreats(): ProtectionStatus {
        return ProtectionStatus(
            overlayApps = detectOverlayApps(),
            accessibilityAbusers = detectAccessibilityAbusers(),
            deviceAdmins = detectDeviceAdmins(),
            notificationListeners = detectNotificationListeners(),
            installerApps = detectInstallerApps(),
            usageStatsAbusers = detectUsageStatsAbusers(),
            isRealTimeProtectionActive = ShieldService.isRunning(),
            lastChecked = System.currentTimeMillis()
        )
    }

    fun detectOverlayApps(): List<ShieldThreat> {
        val threats = mutableListOf<ShieldThreat>()
        try {
            val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            for (app in apps) {
                if (app.packageName == selfPackage) continue
                if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
                val hasOverlay = try {
                    pm.checkPermission(
                        android.Manifest.permission.SYSTEM_ALERT_WINDOW,
                        app.packageName
                    ) == PackageManager.PERMISSION_GRANTED
                } catch (e: Exception) { false }

                if (hasOverlay) {
                    val appName = pm.getApplicationLabel(app).toString()
                    threats.add(
                        ShieldThreat(
                            type = ShieldThreatType.OVERLAY_APP,
                            packageName = app.packageName,
                            appName = appName,
                            details = "App com permissão de sobreposição (pode capturar telas de bancos)",
                            severity = 65
                        )
                    )
                }
            }
        } catch (e: Exception) { }
        return threats
    }

    fun detectAccessibilityAbusers(): List<ShieldThreat> {
        val threats = mutableListOf<ShieldThreat>()
        try {
            val a11yManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val enabledServices = a11yManager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                )
                for (service in enabledServices) {
                    val pkg = service.resolveInfo.serviceInfo.packageName
                    if (pkg == selfPackage) continue
                    try {
                        val appName = pm.getApplicationLabel(
                            pm.getApplicationInfo(pkg, 0)
                        ).toString()
                        val caps = service.capabilities
                        val details = buildString {
                            append("Serviço de acessibilidade ativo")
                            if (caps and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT != 0) {
                                append(" - PODE LER TELA")
                            }
                        }
                        threats.add(
                            ShieldThreat(
                                type = ShieldThreatType.ACCESSIBILITY_ABUSE,
                                packageName = pkg,
                                appName = appName,
                                details = details,
                                severity = if (caps and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT != 0) 85 else 50
                            )
                        )
                    } catch (e: Exception) { }
                }
            } else {
                @Suppress("DEPRECATION")
                val a11yStr = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return threats
                val services = a11yStr.split(":")
                for (entry in services) {
                    if (entry.isBlank()) continue
                    val parts = entry.split("/")
                    if (parts.size < 2) continue
                    val pkg = parts[0]
                    if (pkg == selfPackage) continue
                    try {
                        val appName = pm.getApplicationLabel(
                            pm.getApplicationInfo(pkg, 0)
                        ).toString()
                        threats.add(
                            ShieldThreat(
                                type = ShieldThreatType.ACCESSIBILITY_ABUSE,
                                packageName = pkg,
                                appName = appName,
                                details = "Serviço de acessibilidade ativo (pode interceptar digitação e senhas)",
                                severity = 80
                            )
                        )
                    } catch (e: Exception) { }
                }
            }
        } catch (e: Exception) { }
        return threats
    }

    fun detectDeviceAdmins(): List<ShieldThreat> {
        val threats = mutableListOf<ShieldThreat>()
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admins = dpm.activeAdmins ?: return threats
            for (cn in admins) {
                val pkg = cn.packageName
                if (pkg == selfPackage) continue
                try {
                    val appName = pm.getApplicationLabel(
                        pm.getApplicationInfo(pkg, 0)
                    ).toString()
                    threats.add(
                        ShieldThreat(
                            type = ShieldThreatType.DEVICE_ADMIN_ABUSE,
                            packageName = pkg,
                            appName = appName,
                            details = "Admin do dispositivo ativo - pode travar tela ou limpar dados",
                            severity = 90
                        )
                    )
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
        return threats
    }

    fun detectNotificationListeners(): List<ShieldThreat> {
        val threats = mutableListOf<ShieldThreat>()
        try {
            val enabledListeners = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val nls = context.getSystemService(Context.NOTIFICATION_SERVICE)
                val method = nls!!::class.java.getMethod("getEnabledNotificationListeners")
                @Suppress("UNCHECKED_CAST")
                (method.invoke(nls) as? List<ComponentName>)?.toList() ?: emptyList()
            } else {
                @Suppress("DEPRECATION")
                val str = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                ) ?: ""
                if (str.isBlank()) emptyList()
                else str.split(":").filter { it.isNotBlank() }.map {
                    ComponentName.unflattenFromString(it)
                }.filterNotNull()
            }

            for (cn in enabledListeners) {
                val pkg = cn.packageName
                if (pkg == selfPackage) continue
                try {
                    val appName = pm.getApplicationLabel(
                        pm.getApplicationInfo(pkg, 0)
                    ).toString()
                    threats.add(
                        ShieldThreat(
                            type = ShieldThreatType.NOTIFICATION_LISTENER,
                            packageName = pkg,
                            appName = appName,
                            details = "Pode ler notificações (inclusive códigos 2FA de bancos)",
                            severity = 75
                        )
                    )
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
        return threats
    }

    fun detectInstallerApps(): List<ShieldThreat> {
        val threats = mutableListOf<ShieldThreat>()
        try {
            val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            for (app in apps) {
                if (app.packageName == selfPackage) continue
                if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
                val canInstall = try {
                    pm.checkPermission(
                        android.Manifest.permission.INSTALL_PACKAGES,
                        app.packageName
                    ) == PackageManager.PERMISSION_GRANTED
                } catch (e: Exception) { false }

                if (canInstall) {
                    val appName = pm.getApplicationLabel(app).toString()
                    threats.add(
                        ShieldThreat(
                            type = ShieldThreatType.INSTALLER_APP,
                            packageName = app.packageName,
                            appName = appName,
                            details = "App com permissão de instalar pacotes (pode instalar malware)",
                            severity = 80
                        )
                    )
                }
            }
        } catch (e: Exception) { }
        return threats
    }

    fun detectUsageStatsAbusers(): List<ShieldThreat> {
        val threats = mutableListOf<ShieldThreat>()
        try {
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            for (app in apps) {
                if (app.packageName == selfPackage) continue
                if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
                try {
                    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        appOpsManager.unsafeCheckOpNoThrow(
                            AppOpsManager.OPSTR_GET_USAGE_STATS,
                            app.uid,
                            app.packageName
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        val m = AppOpsManager::class.java
                            .getMethod("checkOpNoThrow", Int::class.java, Int::class.java, String::class.java)
                            .invoke(appOpsManager, 43, app.uid, app.packageName) as Int
                        m
                    }
                    if (mode == AppOpsManager.MODE_ALLOWED) {
                        val appName = pm.getApplicationLabel(app).toString()
                        threats.add(
                            ShieldThreat(
                                type = ShieldThreatType.USAGE_STATS_ABUSE,
                                packageName = app.packageName,
                                appName = appName,
                                details = "App com acesso a estatísticas de uso (monitora apps usados)",
                                severity = 40
                            )
                        )
                    }
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
        return threats
    }

    fun isOverlayActive(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else false
    }
}
