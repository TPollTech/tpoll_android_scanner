// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.tpoll.scanner.data.AppDatabase
import com.tpoll.scanner.notifications.NotificationHelper
import com.tpoll.scanner.protection.LicenseValidator
import com.tpoll.scanner.protection.PackageReceiver
import com.tpoll.scanner.protection.SelfProtection
import com.tpoll.scanner.protection.ShieldService
import com.tpoll.scanner.updater.RemoteConfig
import com.tpoll.scanner.updater.UpdateScheduler
import com.tpoll.scanner.updater.UpdateStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TPollApp : Application() {

    lateinit var notificationHelper: NotificationHelper
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var remoteConfig: RemoteConfig
        private set
    lateinit var ttsHelper: TtsHelper
        private set
    lateinit var selfProtection: SelfProtection
        private set

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        notificationHelper = NotificationHelper(this)
        remoteConfig = RemoteConfig(this)
        ttsHelper = TtsHelper(this)
        selfProtection = SelfProtection(this)
        LicenseValidator.checkAndStoreSignature(this)
        ShieldService.start(this)
        selfProtection.enableProtection()
        UpdateStateStore.reconcileInstalledVersion(this)
        UpdateScheduler.schedule(this)
        registerPackageReceiver()
        refreshRemoteConfig()
    }

    private fun registerPackageReceiver() {
        val receiver = PackageReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun refreshRemoteConfig() {
        scope.launch {
            try {
                remoteConfig.refresh()
            } catch (_: Exception) { }
        }
    }

    companion object {
        lateinit var instance: TPollApp
            private set
    }
}
