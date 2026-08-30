package com.jengadirect.scheduler

import android.content.Context

/** Small key/value store for user preferences and notification state. */
object Prefs {
    private const val FILE = "scheduler_prefs"
    private const val KEY_NEXT_UP_ENABLED = "next_up_enabled"
    private const val KEY_NEXT_UP_DISMISSED = "next_up_dismissed_key"

    private fun sp(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Whether the ongoing "Next up" summary notification should be shown at all. */
    fun nextUpEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_NEXT_UP_ENABLED, true)

    fun setNextUpEnabled(context: Context, enabled: Boolean) =
        sp(context).edit().putBoolean(KEY_NEXT_UP_ENABLED, enabled).apply()

    /**
     * Identity of the "Next up" notification the user last swiped away.
     * While the current next slot still matches this key the notification stays
     * hidden; it reappears once a different slot (or a rescheduled time) becomes
     * next.
     */
    fun nextUpDismissedKey(context: Context): String? =
        sp(context).getString(KEY_NEXT_UP_DISMISSED, null)

    fun setNextUpDismissedKey(context: Context, key: String?) =
        sp(context).edit().putString(KEY_NEXT_UP_DISMISSED, key).apply()

    fun nextUpKey(slotId: Long, startTime: Long): String = "$slotId:$startTime"
}
