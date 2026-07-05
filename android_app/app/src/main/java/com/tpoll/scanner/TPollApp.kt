package com.tpoll.scanner

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.tpoll.scanner.data.AppDatabase
import com.tpoll.scanner.notifications.NotificationHelper
import com.tpoll.scanner.protection.LicenseValidator
import com.tpoll.scanner.protection.PackageReceiver
import com.tpoll.scanner.protection.ShieldService
import com.tpoll.scanner.updater.RemoteConfig
import com.tpoll.scanner.updater.UpdateChecker
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

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        notificationHelper = NotificationHelper(this)
        remoteConfig = RemoteConfig(this)
        ttsHelper = TtsHelper(this)
        LicenseValidator.checkAndStoreSignature(this)
        ShieldService.start(this)
        UpdateChecker.init(this)
        registerPackageReceiver()
        checkForUpdatesBackground()
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

    private fun checkForUpdatesBackground() {
        if (!UpdateChecker.shouldCheck(this)) return
        scope.launch {
            try {
                val result = UpdateChecker().checkForUpdatesWithRetry(this@TPollApp)
                if (result is com.tpoll.scanner.updater.UpdateResult.Available) {
                    notificationHelper.showUpdateAvailable(
                        result.info.version_name,
                        result.info.changelog,
                        result.info.apk_url.ifEmpty { result.info.download_url }
                    )
                    UpdateChecker.markChecked(this@TPollApp)
                }
            } catch (_: Exception) { }
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
