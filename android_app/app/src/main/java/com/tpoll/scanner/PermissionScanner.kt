package com.tpoll.scanner

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo

data class AppPermissionInfo(
    val packageName: String,
    val appName: String,
    val icon: String = "",
    val dangerousPermissions: List<DangerousPerm> = emptyList(),
    val totalDangerous: Int = 0
)

data class DangerousPerm(
    val name: String,
    val label: String,
    val description: String
)

object PermissionScanner {
    private val DANGEROUS_PERMISSIONS = setOf(
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.READ_CONTACTS",
        "android.permission.READ_CALL_LOG",
        "android.permission.READ_PHONE_STATE",
        "android.permission.CALL_PHONE",
        "android.permission.PROCESS_OUTGOING_CALLS",
        "android.permission.READ_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_MMS",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.BODY_SENSORS",
        "android.permission.ACTIVITY_RECOGNITION",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.POST_NOTIFICATIONS"
    )

    fun scanAll(context: Context): List<AppPermissionInfo> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps.mapNotNull { app ->
            try {
                val perms = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                    ?.requestedPermissions?.toList() ?: emptyList()
                val dangerous = perms.filter { it in DANGEROUS_PERMISSIONS }.mapNotNull { perm ->
                    try {
                        val info = pm.getPermissionInfo(perm, 0)
                        DangerousPerm(
                            name = perm,
                            label = info.loadLabel(pm).toString().ifEmpty { perm },
                            description = info.loadDescription(pm)?.toString() ?: ""
                        )
                    } catch (_: Exception) {
                        DangerousPerm(perm, perm.split(".").last(), "")
                    }
                }
                if (dangerous.isEmpty()) return@mapNotNull null
                AppPermissionInfo(
                    packageName = app.packageName,
                    appName = pm.getApplicationLabel(app).toString(),
                    icon = "",
                    dangerousPermissions = dangerous.sortedBy { it.label },
                    totalDangerous = dangerous.size
                )
            } catch (_: Exception) { null }
        }.sortedByDescending { it.totalDangerous }
    }
}
