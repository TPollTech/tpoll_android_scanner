package com.tpoll.scanner.updater

import android.content.Context
import android.os.Build

data class InstalledAppVersion(
    val code: Long,
    val name: String
) {
    companion object {
        fun read(context: Context): InstalledAppVersion {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            return InstalledAppVersion(
                code = versionCode,
                name = packageInfo.versionName ?: "desconhecida"
            )
        }
    }
}
