package com.jengadirect.scheduler.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.jengadirect.scheduler.Prefs
import com.jengadirect.scheduler.R
import com.jengadirect.scheduler.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles notification actions:
 *  - the "Confirm" button on a slot alert
 *  - the user swiping away the ongoing "Next up" notification
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            ACTION_DISMISS_NEXT_UP -> {
                val key = intent.getStringExtra(EXTRA_KEY)
                Prefs.setNextUpDismissedKey(app, key)
            }

            ACTION_CONFIRM -> {
                val slotId = intent.getLongExtra(EXTRA_SLOT_ID, -1L)
                if (slotId < 0L) return
                val pending = goAsync()
                AppScope.io.launch {
                    try {
                        val repo = Repository(app)
                        repo.confirmSlot(slotId)
                        AlarmScheduler.cancel(app, slotId)
                        Notifications.cancelSlotAlerts(app, slotId)
                        Notifications.showNextUp(app, repo.nextUpcoming())
                        withContext(Dispatchers.Main) {
                            Toast.makeText(app, R.string.confirmed_toast, Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_CONFIRM = "com.jengadirect.scheduler.action.CONFIRM"
        const val ACTION_DISMISS_NEXT_UP = "com.jengadirect.scheduler.action.DISMISS_NEXT_UP"
        private const val EXTRA_SLOT_ID = "slot_id"
        private const val EXTRA_REMINDER = "reminder"
        private const val EXTRA_KEY = "key"

        fun confirmIntent(context: Context, slotId: Long, reminder: Boolean): Intent =
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_CONFIRM
                setPackage(context.packageName)
                putExtra(EXTRA_SLOT_ID, slotId)
                putExtra(EXTRA_REMINDER, reminder)
            }

        fun dismissNextUpIntent(context: Context, key: String): Intent =
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_DISMISS_NEXT_UP
                setPackage(context.packageName)
                putExtra(EXTRA_KEY, key)
            }
    }
}
