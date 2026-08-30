package com.jengadirect.scheduler.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jengadirect.scheduler.data.Repository
import kotlinx.coroutines.launch

/** Re-arms all future slot alarms after a reboot or app update. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }

        val pending = goAsync()
        val app = context.applicationContext
        AppScope.io.launch {
            try {
                Notifications.createChannels(app)
                val repo = Repository(app)
                repo.markPastSlotsMissed()
                repo.futurePendingSlots().forEach { (slot, name) ->
                    AlarmScheduler.schedule(app, slot, name)
                }
                Notifications.showNextUp(app, repo.nextUpcoming())
            } finally {
                pending.finish()
            }
        }
    }
}
