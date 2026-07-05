package com.tpoll.scanner

import android.app.Application
import com.tpoll.scanner.notifications.NotificationHelper

class TPollApp : Application() {

    lateinit var notificationHelper: NotificationHelper
        private set

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
    }
}
