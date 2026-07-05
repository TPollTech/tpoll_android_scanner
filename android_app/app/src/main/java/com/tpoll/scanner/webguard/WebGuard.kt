package com.tpoll.scanner.webguard

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import com.tpoll.scanner.TPollApp
import com.tpoll.scanner.model.QuarantinedApp
import com.tpoll.scanner.protection.ShieldDetector
import com.tpoll.scanner.protection.ShieldThreat
import com.tpoll.scanner.protection.ShieldThreatType
import java.io.File

data class DownloadedApk(
    val path: String,
    val fileName: String,
    val packageName: String = "",
    val isMalicious: Boolean = false,
    val threat: ShieldThreat? = null
)

class WebGuard(private val context: Context) {

    private val prefs = context.getSharedPreferences("webguard_prefs", Context.MODE_PRIVATE)
    private val detector = ShieldDetector(context)
    private val pm: PackageManager = context.packageManager

    fun isEnabled(): Boolean = prefs.getBoolean("webguard_enabled", false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("webguard_enabled", enabled).apply()
    }

    fun scanDownloads(): List<DownloadedApk> {
        if (!isEnabled()) return emptyList()

        val results = mutableListOf<DownloadedApk>()
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) return emptyList()

            val apkFiles = downloadsDir.listFiles { f -> f.name.endsWith(".apk") && !f.name.startsWith(".tpoll_") }
                ?: return emptyList()

            val scannedKey = prefs.getString("scanned_files", "") ?: ""
            val scannedFiles = scannedKey.split(",").filter { it.isNotBlank() }.toMutableSet()

            for (file in apkFiles) {
                if (file.name in scannedFiles) continue

                try {
                    val pkgInfo = pm.getPackageArchiveInfo(file.absolutePath, 0)
                    val pkgName = pkgInfo?.packageName ?: ""

                    val threat = analyzeApk(file.absolutePath)
                    val isMalicious = threat != null

                    scannedFiles.add(file.name)
                    results.add(
                        DownloadedApk(
                            path = file.absolutePath,
                            fileName = file.name,
                            packageName = pkgName,
                            isMalicious = isMalicious,
                            threat = threat
                        )
                    )

                    if (isMalicious) {
                        val app = context.applicationContext as TPollApp
                        app.notificationHelper.showWebGuardAlert(
                            fileName = file.name,
                            threatDetail = threat?.details ?: "APK malicioso detectado"
                        )

                        kotlinx.coroutines.runBlocking {
                            app.database.quarantineDao().insert(
                                QuarantinedApp(
                                    packageName = pkgName.ifEmpty { file.name },
                                    appName = file.name,
                                    reason = threat?.details ?: "APK malicioso baixado automaticamente",
                                    riskLevel = "HIGH",
                                    score = threat?.severity ?: 90,
                                    removedBy = "webguard"
                                )
                            )
                        }
                    }
                } catch (_: Exception) { }
            }

            prefs.edit().putString("scanned_files", scannedFiles.joinToString(",")).apply()
        } catch (_: Exception) { }

        return results
    }

    private fun analyzeApk(path: String): ShieldThreat? {
        return try {
            val pkgInfo = pm.getPackageArchiveInfo(path, PackageManager.GET_PERMISSIONS)
                ?: return ShieldThreat(
                    type = ShieldThreatType.KNOWN_MALWARE,
                    packageName = "unknown",
                    appName = File(path).name,
                    details = "APK inválido ou corrompido",
                    severity = 80,
                    isMalware = true
                )

            val perms = pkgInfo.requestedPermissions?.toList() ?: emptyList()
            val highRiskPerms = listOf(
                "android.permission.SYSTEM_ALERT_WINDOW",
                "android.permission.BIND_ACCESSIBILITY_SERVICE",
                "android.permission.REQUEST_INSTALL_PACKAGES",
                "android.permission.RECEIVE_SMS",
                "android.permission.READ_SMS"
            )
            val found = highRiskPerms.filter { it in perms }

            if (found.size >= 3) {
                ShieldThreat(
                    type = ShieldThreatType.KNOWN_MALWARE,
                    packageName = pkgInfo.packageName ?: "unknown",
                    appName = File(path).name,
                    details = "APK baixado automaticamente com ${found.size} permissões de alto risco: ${found.joinToString(", ")}",
                    severity = 70 + (found.size * 5),
                    isMalware = true
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clearScannedCache() {
        prefs.edit().remove("scanned_files").apply()
    }
}
