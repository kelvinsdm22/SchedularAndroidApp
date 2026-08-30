package com.jengadirect.scheduler.alarm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Shared scope for short DB work started from BroadcastReceivers via goAsync(). */
object AppScope {
    val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
