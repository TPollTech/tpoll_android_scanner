package com.tpoll.scanner

import android.app.Application
import com.tpoll.scanner.notifications.NotificationHelper
import com.tpoll.scanner.protection.ShieldService
import com.tpoll.scanner.updater.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TPollApp : Application() {

    lateinit var notificationHelper: NotificationHelper
        private set

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        ShieldService.start(this)
        UpdateChecker.init(this)
        checkForUpdatesBackground()
    }

    private fun checkForUpdatesBackground() {
        if (!UpdateChecker.shouldCheck(this)) return
        scope.launch {
            try {
                val result = UpdateChecker().checkForUpdates()
                if (result is com.tpoll.scanner.updater.UpdateResult.Available) {
                    notificationHelper.showUpdateAvailable(
                        result.info.version_name,
                        result.info.changelog
                    )
                    UpdateChecker.markChecked(this@TPollApp)
                }
            } catch (_: Exception) { }
        }
    }
}
