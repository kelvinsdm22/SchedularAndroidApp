package com.jengadirect.scheduler.ui

import android.content.Context
import com.jengadirect.scheduler.R
import com.jengadirect.scheduler.data.SlotStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dayFmt: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())
    private val timeFmt: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    fun day(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(dayFmt)

    fun time(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(timeFmt)

    fun statusLabel(context: Context, status: SlotStatus): String = context.getString(
        when (status) {
            SlotStatus.PENDING -> R.string.status_pending
            SlotStatus.CONFIRMED -> R.string.status_confirmed
            SlotStatus.POSTPONED -> R.string.status_postponed
            SlotStatus.MISSED -> R.string.status_missed
        }
    )

    fun statusColor(status: SlotStatus): Int = when (status) {
        SlotStatus.PENDING -> R.color.status_pending
        SlotStatus.CONFIRMED -> R.color.status_confirmed
        SlotStatus.POSTPONED -> R.color.status_postponed
        SlotStatus.MISSED -> R.color.status_missed
    }
}
