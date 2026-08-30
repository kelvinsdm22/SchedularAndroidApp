package com.jengadirect.scheduler

import android.app.Application
import com.jengadirect.scheduler.alarm.Notifications

class SchedulerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
