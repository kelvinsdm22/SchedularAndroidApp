package com.jengadirect.scheduler.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jengadirect.scheduler.Prefs
import com.jengadirect.scheduler.R
import com.jengadirect.scheduler.data.NextUp
import com.jengadirect.scheduler.data.Slot
import com.jengadirect.scheduler.ui.MainActivity
import com.jengadirect.scheduler.ui.TaskDetailActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Notifications {

    const val CHANNEL_ALARM = "slot_alarms"
    const val CHANNEL_ONGOING = "next_up"
    const val ONGOING_ID = 1

    private val dayFmt: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    private val timeFmt: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return

        val alarm = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_alarm_desc)
            enableVibration(true)
        }
        val ongoing = NotificationChannel(
            CHANNEL_ONGOING,
            context.getString(R.string.channel_ongoing_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_ongoing_desc)
            setShowBadge(false)
        }
        mgr.createNotificationChannel(alarm)
        mgr.createNotificationChannel(ongoing)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Stable notification id for a slot alert. */
    fun slotNotificationId(slotId: Long, reminder: Boolean): Int =
        (slotId.toInt() * 2) + (if (reminder) 100_000 else 200_000)

    @SuppressLint("MissingPermission") // guarded by canPost()
    fun showSlotAlert(
        context: Context,
        slot: Slot,
        taskName: String,
        reminder: Boolean
    ) {
        if (!canPost(context)) return
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(slot.startTime).atZone(zone)
        val end = Instant.ofEpochMilli(slot.endTime).atZone(zone)
        val range = "${start.format(timeFmt)} – ${end.format(timeFmt)}"

        val title = context.getString(
            if (reminder) R.string.notif_reminder_title_fmt else R.string.notif_start_title_fmt,
            taskName
        )

        val contentIntent = PendingIntent.getActivity(
            context,
            slotNotificationId(slot.id, reminder),
            TaskDetailActivity.intent(context, slot.taskId),
            piFlags(update = false)
        )

        val confirmIntent = PendingIntent.getBroadcast(
            context,
            slotNotificationId(slot.id, reminder) + 1,
            NotificationActionReceiver.confirmIntent(context, slot.id, reminder),
            piFlags(update = true)
        )

        val postponeIntent = PendingIntent.getActivity(
            context,
            slotNotificationId(slot.id, reminder) + 2,
            TaskDetailActivity.rescheduleIntent(context, slot.taskId, slot.id),
            piFlags(update = true)
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_slot_text_fmt, range))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_check, context.getString(R.string.confirm_attendance), confirmIntent)
            .addAction(R.drawable.ic_schedule, context.getString(R.string.postpone), postponeIntent)
            .build()

        NotificationManagerCompat.from(context)
            .notify(slotNotificationId(slot.id, reminder), notification)
    }

    fun cancelSlotAlerts(context: Context, slotId: Long) {
        val mgr = NotificationManagerCompat.from(context)
        mgr.cancel(slotNotificationId(slotId, reminder = true))
        mgr.cancel(slotNotificationId(slotId, reminder = false))
    }

    @SuppressLint("MissingPermission") // guarded by canPost()
    fun showNextUp(context: Context, next: NextUp?) {
        val mgr = NotificationManagerCompat.from(context)
        if (next == null || !canPost(context) || !Prefs.nextUpEnabled(context)) {
            mgr.cancel(ONGOING_ID)
            return
        }

        val key = Prefs.nextUpKey(next.slot.id, next.slot.startTime)
        if (Prefs.nextUpDismissedKey(context) == key) {
            // User swiped this one away and the next slot hasn't changed since.
            mgr.cancel(ONGOING_ID)
            return
        }

        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(next.slot.startTime).atZone(zone)
        val whenText = context.getString(
            R.string.notif_next_up_time_fmt,
            start.format(dayFmt),
            start.format(timeFmt)
        )
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            piFlags(update = false)
        )
        val deleteIntent = PendingIntent.getBroadcast(
            context,
            ONGOING_ID,
            NotificationActionReceiver.dismissNextUpIntent(context, key),
            piFlags(update = true)
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_next_up_fmt, next.taskName))
            .setContentText(whenText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .setAutoCancel(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .build()
        mgr.notify(ONGOING_ID, notification)
    }

    private fun piFlags(update: Boolean): Int {
        var flags = if (update) PendingIntent.FLAG_UPDATE_CURRENT else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return flags
    }
}
