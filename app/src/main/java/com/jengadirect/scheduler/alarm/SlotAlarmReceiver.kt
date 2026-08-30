package com.jengadirect.scheduler.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jengadirect.scheduler.data.Repository
import com.jengadirect.scheduler.data.SlotStatus
import kotlinx.coroutines.launch

/** Fired by AlarmManager at reminder time and slot-start time. */
class SlotAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val slotId = intent.getLongExtra(EXTRA_SLOT_ID, -1L)
        val reminder = intent.getBooleanExtra(EXTRA_REMINDER, false)
        if (slotId < 0L) return

        val pending = goAsync()
        val app = context.applicationContext
        AppScope.io.launch {
            try {
                val repo = Repository(app)
                val slot = repo.getSlot(slotId)
                if (slot != null &&
                    (slot.status == SlotStatus.PENDING || slot.status == SlotStatus.POSTPONED)
                ) {
                    val task = repo.getTask(slot.taskId)
                    if (task != null) {
                        Notifications.showSlotAlert(app, slot, task.name, reminder)
                    }
                }
                Notifications.showNextUp(app, repo.nextUpcoming())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_SLOT_ID = "slot_id"
        private const val EXTRA_REMINDER = "reminder"

        fun intent(context: Context, slotId: Long, reminder: Boolean): Intent =
            Intent(context, SlotAlarmReceiver::class.java).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_SLOT_ID, slotId)
                putExtra(EXTRA_REMINDER, reminder)
            }
    }
}
