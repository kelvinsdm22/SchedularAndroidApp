package com.jengadirect.scheduler.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import com.jengadirect.scheduler.data.Slot

/**
 * Arms two exact alarms per slot: one 15 minutes before the start, one at the start.
 * Falls back to an inexact window when the OS withholds the exact-alarm permission,
 * and to setExact() on API 21-22.
 */
object AlarmScheduler {

    private const val REMINDER_LEAD_MS = 15 * 60_000L

    /** [taskName] is accepted for call-site clarity; the receiver re-reads task data from the DB. */
    fun schedule(context: Context, slot: Slot, @Suppress("UNUSED_PARAMETER") taskName: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()

        val reminderAt = slot.startTime - REMINDER_LEAD_MS
        if (reminderAt > now) {
            setAlarm(context, am, reminderAt, requestCode(slot.id, reminder = true), slot.id, reminder = true)
        }
        if (slot.startTime > now) {
            setAlarm(context, am, slot.startTime, requestCode(slot.id, reminder = false), slot.id, reminder = false)
        }
    }

    fun cancel(context: Context, slotId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        for (reminder in listOf(true, false)) {
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode(slotId, reminder),
                SlotAlarmReceiver.intent(context, slotId, reminder),
                cancelFlags()
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }
        Notifications.cancelSlotAlerts(context, slotId)
    }

    private fun setAlarm(
        context: Context,
        am: AlarmManager,
        triggerAt: Long,
        requestCode: Int,
        slotId: Long,
        reminder: Boolean
    ) {
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            SlotAlarmReceiver.intent(context, slotId, reminder),
            scheduleFlags()
        )
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms() ->
                    am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60_000L, pi)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                else ->
                    am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun requestCode(slotId: Long, reminder: Boolean): Int =
        (slotId.toInt() * 2) + if (reminder) 1 else 0

    private fun scheduleFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return flags
    }

    private fun cancelFlags(): Int {
        var flags = PendingIntent.FLAG_NO_CREATE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return flags
    }
}
