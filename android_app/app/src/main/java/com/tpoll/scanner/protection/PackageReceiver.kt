package com.tpoll.scanner.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PackageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return
        if (packageName == context.packageName) return

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                if (!ShieldService.isRunning()) return
                Intent(context, ShieldService::class.java).apply {
                    action = ShieldService.ACTION_SCAN_NOW
                    putExtra("NEW_PACKAGE", packageName)
                }.also { context.startService(it) }
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                val prefs = context.getSharedPreferences("protection_status", Context.MODE_PRIVATE)
                prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
            }
        }
    }
}
