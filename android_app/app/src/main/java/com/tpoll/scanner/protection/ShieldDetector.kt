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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ShieldThreat(
    val type: ShieldThreatType,
    val packageName: String,
    val appName: String,
    val details: String,
    val severity: Int,
    val isMalware: Boolean = false
)

enum class ShieldThreatType {
    OVERLAY_APP,
    ACCESSIBILITY_ABUSE,
    DEVICE_ADMIN_ABUSE,
    NOTIFICATION_LISTENER,
    INSTALLER_APP,
    USAGE_STATS_ABUSE,
    KNOWN_MALWARE
}

data class ProtectionStatus(
    val threats: List<ShieldThreat> = emptyList(),
    val isRealTimeProtectionActive: Boolean = false,
    val lastChecked: Long = 0L
) {
    val totalThreats: Int get() = threats.size
    val hasCriticalThreats: Boolean get() = threats.any { it.severity >= 70 }
    val hasMalware: Boolean get() = threats.any { it.isMalware }
}

class ShieldDetector(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val selfPackage = context.packageName
    private val gson = Gson()

    companion object {
        private val TRUSTED_PREFIXES = setOf(
            "com.android.", "com.google.", "com.samsung.", "com.google.android.",
            "com.google.android.apps.", "com.sec.android.", "com.sonymobile.",
            "com.xiaomi.", "com.huawei.", "com.oneplus.", "com.oppo.",
            "com.vivo.", "com.lge.", "com.motorola.", "com.qualcomm.",
            "com.mediatek.", "com.nxp.", "com.facebook.", "com.facebook.katana",
            "com.facebook.orca", "com.whatsapp", "com.instagram.android",
            "com.microsoft.", "com.skype.", "com.linkedin.android",
            "com.amazon.", "com.netflix.", "com.spotify.",
            "com.twitter.android", "com.android.chrome", "org.chromium.",
            "com.google.android.youtube", "com.google.android.gm",
            "com.google.android.apps.maps", "com.google.android.apps.photos",
            "com.google.android.apps.docs", "com.google.android.apps.messaging",
            "com.google.android.dialer", "com.google.android.contacts",
            "com.android.systemui", "com.android.settings",
            "com.android.launcher", "com.android.vending",
            "com.android.packageinstaller", "com.android.permissioncontroller",
            "com.android.providers.", "com.android.server.",
            "com.android.cellbroadcast", "com.android.bluetooth",
            "com.android.carrierconfig", "com.android.emergency",
            "com.android.inputdevices", "com.android.keychain",
            "com.android.location.fused", "com.android.managedprovisioning",
            "com.android.nfc", "com.android.onetimeinitializer",
            "com.android.phone", "com.android.printspooler",
            "com.android.proxyhandler", "com.android.sharedstoragebackup",
            "com.android.shell", "com.android.statementservice",
            "com.android.wallpaper.", "com.android.wifi",
            "com.android.captiveportallogin", "com.android.documentui",
            "com.android.calculator2", "com.android.calendar",
            "com.android.camera2", "com.android.contacts",
            "com.android.deskclock", "com.android.email",
            "com.android.fileexplorer", "com.android.gallery3d",
            "com.android.mms", "com.android.music",
            "com.android.quicksearchbox", "com.android.soundrecorder",
            "com.android.stk", "com.android.voicedialer",
            "com.sec.android.app.samsungapps", "com.sec.android.gallery3d",
            "com.sec.android.app.camera", "com.sec.android.app.clockpackage",
            "com.sec.android.app.calculator", "com.sec.android.app.myfiles",
            "com.sec.android.app.popupcalculator", "com.sec.android.app.sbrowser",
            "com.sec.android.app.voicenote", "com.sec.android.provider.",
            "com.samsung.android.", "com.samsung.accessibility.",
            "com.samsung.voiceservice", "com.sec.enterprise.",
            "com.wssyncmldm", "com.samsung.sdm",
            "com.osp.app.signin", "com.samsung.android.mateagent",
            "com.samsung.knox.", "com.samsung.android.knox.",
            "com.samsung.android.security.",
            "com.google.android.gms", "com.google.android.gsf",
            "com.google.android.gms.policy_sidecar_aps",
            "com.google.android.onetimeinitializer",
            "com.google.android.packageinstaller",
            "com.google.android.configupdater",
            "com.google.android.syncadapters.",
            "com.google.android.setupwizard",
        )

        private fun isTrusted(packageName: String): Boolean {
            return TRUSTED_PREFIXES.any { packageName.startsWith(it) }
        }
    }

    fun detectAllThreats(): ProtectionStatus {
        val startTime = System.currentTimeMillis()
        val threats = mutableListOf<ShieldThreat>()

        threats.addAll(detectKnownMalware())
        threats.addAll(detectDangerousCombos())
        threats.addAll(detectDeviceAdmins())
        threats.addAll(detectAccessibilityBySuspiciousApps())
        threats.addAll(detectSuspiciousOverlayApps())

        return ProtectionStatus(
            threats = threats.sortedByDescending { it.severity },
            isRealTimeProtectionActive = ShieldService.isRunning(),
            lastChecked = System.currentTimeMillis()
        )
    }

    private fun detectKnownMalware(): List<ShieldThreat> {
        val threats = mutableListOf<ShieldThreat>()
        try {
            val virusDb = loadVirusDb()
            val apps = installedNonSystemApps()

            for (app in apps) {
                val entry = virusDb.known_threats[app.packageName]
                if (entry != null) {
                    val appName = pm.getApplicationLabel(app).toString()
                    threats.add(
                        ShieldThreat(
                            type = ShieldThreatType.KNOWN_MALWARE,
                            packageName = app.packageName,
                            appName = appName,
                            details = entry.description,
                            severity = if (entry.severity == "high") 95 else if (entry.severity == "medium") 75 else 50,
                            isMalware = true
                        )
                    )
                    continue
                }

                for (pattern in virusDb.suspicious_patterns) {
                    try {
                        if (app.packageName.matches(Regex(pattern.pattern))) {
                            val appName = pm.getApplicationLabel(app).toString()
                            threats.add(
                                ShieldThreat(
                                    type = ShieldThreatType.KNOWN_MALWARE,
                                    packageName = app.packageName,
                                    appName = appName,
                                    details = pattern.reason,
                                    severity = 50,
                                    isMalware = false
                                )
                            )
                        }
                    } catch (e: Exception) { }
                }
            }
        } catch (e: Exception) { }
        return threats
    }

    private fun detectDangerousCombos(): List<ShieldThreat> {
        val threats = mutableListOf<ShieldThreat>()
        try {
            val apps = installedNonSystemApps()

            for (app in apps) {
                if (isTrusted(app.packageName)) continue
                if (app.packageName == selfPackage) continue

                val hasOverlay = hasPermission(app.packageName, android.Manifest.permission.SYSTEM_ALERT_WINDOW)
                val hasAccessibility = isAccessibilityService(app.packageName)
                val hasDeviceAdmin = isDeviceAdmin(app.packageName)
                val hasNotificationListener = isNotificationListener(app.packageName)
                val canInstall = hasPermission(app.packageName, android.Manifest.permission.INSTALL_PACKAGES)
                val hasUsageStats = hasUsageStatsPermission(app.packageName)

                val dangerousFlags = listOfNotNull(
                    hasOverlay to "sobreposição",
                    hasAccessibility to "acessibilidade",
                    hasDeviceAdmin to "admin dispositivo",
                    canInstall to "instalar apps",
                    hasNotificationListener to "ler notificações"
                ).filter { it.first }.map { it.second }

                val appName = pm.getApplicationLabel(app).toString()

                if (hasAccessibility && hasOverlay) {
                    threats.add(
                        ShieldThreat(
                            type = ShieldThreatType.ACCESSIBILITY_ABUSE,
                            packageName = app.packageName,
                            appName = appName,
                            details = "Acessibilidade + sobreposição: pode capturar senhas e telas de bancos",
                            severity = 80,
                            isMalware = true
                        )
                    )
                } else if (hasDeviceAdmin && !hasTrustedInstaller(app.packageName)) {
                    threats.add(
                        ShieldThreat(
                            type = ShieldThreatType.DEVICE_ADMIN_ABUSE,
                            packageName = app.packageName,
                            appName = appName,
                            details = "Admin de dispositivo ativo de app não-confiável - pode travar o celular",
                            severity = 85,
                            isMalware = true
                        )
                    )
                } else if (dangerousFlags.size >= 3) {
                    threats.add(
                        ShieldThreat(
                            type = ShieldThreatType.OVERLAY_APP,
                            packageName = app.packageName,
                            appName = appName,
                            details = "App suspeito com múltiplas permissões: ${dangerousFlags.joinToString(", ")}",
                            severity = 70
                        )
                    )
                }
            }
        } catch (e: Exception) { }
        return threats
    }

    private fun detectDeviceAdmins(): List<ShieldThreat> {
        val threats = mutableListOf<ShieldThreat>()
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admins = dpm.activeAdmins ?: return threats
            for (cn in admins) {
                val pkg = cn.packageName
                if (pkg == selfPackage) continue
                if (isTrusted(pkg)) continue
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    if (threats.none { it.packageName == pkg }) {
                        threats.add(
                            ShieldThreat(
                                type = ShieldThreatType.DEVICE_ADMIN_ABUSE,
                                packageName = pkg,
                                appName = appName,
                                details = "Admin de dispositivo de app não-confiável - pode travar/bloquear o aparelho",
                                severity = 85,
                                isMalware = true
                            )
                        )
                    }
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
        return threats
    }

    private fun detectAccessibilityBySuspiciousApps(): List<ShieldThreat> {
        val threats = mutableListOf<ShieldThreat>()
        try {
            val a11yManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                a11yManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    .map { it.resolveInfo.serviceInfo.packageName }.toSet()
            } else {
                @Suppress("DEPRECATION")
                val a11yStr = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return threats
                a11yStr.split(":").mapNotNull { entry ->
                    val parts = entry.split("/")
                    if (parts.size < 2) null else parts[0]
                }.toSet()
            }

            for (pkg in enabledPkgs) {
                if (pkg == selfPackage || isTrusted(pkg)) continue
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val hasOverlay = hasPermission(pkg, android.Manifest.permission.SYSTEM_ALERT_WINDOW)
                    val canInstall = hasPermission(pkg, android.Manifest.permission.INSTALL_PACKAGES)
                    val isUnknownInstaller = !hasTrustedInstaller(pkg)

                    val reason = buildString {
                        append("Serviço de acessibilidade ativo")
                        if (hasOverlay) append(" + sobreposição de tela")
                        if (canInstall) append(" + instala apps")
                        if (isUnknownInstaller) append(" (origem não-confiável)")
                    }

                    if (hasOverlay || canInstall) {
                        threats.add(
                            ShieldThreat(
                                type = ShieldThreatType.ACCESSIBILITY_ABUSE,
                                packageName = pkg,
                                appName = appName,
                                details = reason,
                                severity = if (hasOverlay && canInstall) 85 else 70,
                                isMalware = hasOverlay && canInstall
                            )
                        )
                    }
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
        return threats
    }

    private fun detectSuspiciousOverlayApps(): List<ShieldThreat> {
        val threats = mutableListOf<ShieldThreat>()
        try {
            val apps = installedNonSystemApps()
            for (app in apps) {
                if (isTrusted(app.packageName)) continue
                if (app.packageName == selfPackage) continue

                val hasOverlay = hasPermission(app.packageName, android.Manifest.permission.SYSTEM_ALERT_WINDOW)
                if (!hasOverlay) continue

                val canInstall = hasPermission(app.packageName, android.Manifest.permission.INSTALL_PACKAGES)
                val hasUsageStats = hasUsageStatsPermission(app.packageName)
                val isUnknownInstaller = !hasTrustedInstaller(app.packageName)

                if (canInstall && isUnknownInstaller) {
                    val appName = pm.getApplicationLabel(app).toString()
                    threats.add(
                        ShieldThreat(
                            type = ShieldThreatType.OVERLAY_APP,
                            packageName = app.packageName,
                            appName = appName,
                            details = "App com sobreposição de tela + instalação de apps (pode instalar malware oculto)",
                            severity = 75,
                            isMalware = true
                        )
                    )
                }
            }
        } catch (e: Exception) { }
        return threats
    }

    private fun hasPermission(packageName: String, permission: String): Boolean {
        return try {
            pm.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }
    }

    private fun isAccessibilityService(packageName: String): Boolean {
        return try {
            val a11yManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                a11yManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            } else emptyList()
            services.any { it.resolveInfo.serviceInfo.packageName == packageName }
        } catch (e: Exception) { false }
    }

    private fun isDeviceAdmin(packageName: String): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admins = dpm.activeAdmins ?: return false
            admins.any { it.packageName == packageName }
        } catch (e: Exception) { false }
    }

    private fun isNotificationListener(packageName: String): Boolean {
        return try {
            val enabledListeners = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val nls = context.getSystemService(Context.NOTIFICATION_SERVICE)
                val method = nls!!::class.java.getMethod("getEnabledNotificationListeners")
                @Suppress("UNCHECKED_CAST")
                (method.invoke(nls) as? List<ComponentName>)?.toList() ?: emptyList()
            } else {
                @Suppress("DEPRECATION")
                val str = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                if (str.isNullOrBlank()) emptyList()
                else str.split(":").filter { it.isNotBlank() }.mapNotNull { ComponentName.unflattenFromString(it) }
            }
            enabledListeners.any { it.packageName == packageName }
        } catch (e: Exception) { false }
    }

    private fun hasUsageStatsPermission(packageName: String): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val uid = pm.getApplicationInfo(packageName, 0).uid
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, packageName)
            } else {
                @Suppress("DEPRECATION")
                AppOpsManager::class.java.getMethod("checkOpNoThrow", Int::class.java, Int::class.java, String::class.java)
                    .invoke(appOps, 43, uid, packageName) as Int
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }
    }

    private fun hasTrustedInstaller(packageName: String): Boolean {
        return try {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
            installer != null && (installer == "com.android.vending" || isTrusted(installer))
        } catch (e: Exception) { false }
    }

    private fun installedNonSystemApps(): List<ApplicationInfo> {
        return try {
            val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            apps.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
        } catch (e: Exception) { emptyList() }
    }

    private fun loadVirusDb(): VirusDb {
        return try {
            val json = context.assets.open("virus_db.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<VirusDb>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) { VirusDb() }
    }

    data class VirusDbEntry(
        val type: String = "",
        val severity: String = "low",
        val description: String = ""
    )
    data class VirusDb(
        val known_threats: Map<String, VirusDbEntry> = emptyMap(),
        val suspicious_patterns: List<SuspiciousPattern> = emptyList()
    )
    data class SuspiciousPattern(
        val pattern: String = "",
        val reason: String = ""
    )
}
