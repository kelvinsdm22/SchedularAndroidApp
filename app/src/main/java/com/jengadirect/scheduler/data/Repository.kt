package com.jengadirect.scheduler.data

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max

/** Progress rollup for a task, shown in the list and detail header. */
data class TaskProgress(
    val task: Task,
    val slots: List<Slot>
) {
    val confirmedHours: Int get() = slots.count { it.status == SlotStatus.CONFIRMED } * Slot.SLOT_HOURS
    val plannedHours: Int get() = slots.count { it.countsTowardPlan } * Slot.SLOT_HOURS
    val missingHours: Int get() = max(0, task.durationHours - plannedHours)
    val isComplete: Boolean get() = confirmedHours >= task.durationHours
    val percent: Int
        get() = if (task.durationHours <= 0) 0
        else (confirmedHours * 100 / task.durationHours).coerceIn(0, 100)
}

/** Nearest upcoming slot, for the ongoing "Next up" notification. */
data class NextUp(val slot: Slot, val taskName: String)

class Repository(context: Context) {

    private val db = AppDatabase.get(context)
    private val taskDao = db.taskDao()
    private val slotDao = db.slotDao()

    // ---- Tasks -------------------------------------------------------------

    suspend fun allProgress(): List<TaskProgress> = taskDao.getAll().map { task ->
        TaskProgress(task, slotDao.getForTask(task.id))
    }

    suspend fun progressFor(taskId: Long): TaskProgress? {
        val task = taskDao.getById(taskId) ?: return null
        return TaskProgress(task, slotDao.getForTask(taskId))
    }

    suspend fun getTask(taskId: Long): Task? = taskDao.getById(taskId)

    /** Creates a task and auto-generates enough 4 hr slots to cover its duration. */
    suspend fun createTaskWithSlots(name: String, durationHours: Int, startHour: Int): Long {
        val id = taskDao.insert(
            Task(name = name.trim(), durationHours = durationHours, startHour = startHour)
        )
        val task = taskDao.getById(id) ?: return id
        generateMissingSlots(task, emptyList())
        return id
    }

    /** Updates a task; tops up auto-generated slots if the duration grew. */
    suspend fun updateTask(task: Task): List<Slot> {
        taskDao.update(task)
        val existing = slotDao.getForTask(task.id)
        return generateMissingSlots(task, existing)
    }

    suspend fun deleteTask(taskId: Long) {
        taskDao.getById(taskId)?.let { taskDao.delete(it) } // slots cascade
    }

    // ---- Slots -----------------------------------------------------------

    suspend fun slotsForTask(taskId: Long): List<Slot> = slotDao.getForTask(taskId)

    suspend fun getSlot(slotId: Long): Slot? = slotDao.getById(slotId)

    suspend fun addSlot(taskId: Long, startTime: Long): Slot {
        val id = slotDao.insert(Slot(taskId = taskId, startTime = startTime))
        return slotDao.getById(id)!!
    }

    suspend fun rescheduleSlot(slotId: Long, newStart: Long): Slot? {
        val slot = slotDao.getById(slotId) ?: return null
        val updated = slot.copy(
            startTime = newStart,
            status = if (slot.status == SlotStatus.CONFIRMED) SlotStatus.CONFIRMED else SlotStatus.POSTPONED
        )
        slotDao.update(updated)
        return updated
    }

    suspend fun confirmSlot(slotId: Long): Slot? {
        val slot = slotDao.getById(slotId) ?: return null
        val updated = slot.copy(status = SlotStatus.CONFIRMED)
        slotDao.update(updated)
        return updated
    }

    suspend fun removeSlot(slotId: Long) {
        slotDao.getById(slotId)?.let { slotDao.delete(it) }
    }

    suspend fun markPastSlotsMissed(now: Long = System.currentTimeMillis()): Int =
        slotDao.markMissedBefore(now)

    // ---- Notifications support -----------------------------------------

    suspend fun nextUpcoming(now: Long = System.currentTimeMillis()): NextUp? {
        val candidates = slotDao.getAll()
            .filter { (it.status == SlotStatus.PENDING || it.status == SlotStatus.POSTPONED) && it.endTime > now }
            .sortedBy { it.startTime }
        for (slot in candidates) {
            val task = taskDao.getById(slot.taskId) ?: continue
            return NextUp(slot, task.name)
        }
        return null
    }

    /** All slots that still need an alarm armed (used after reboot). */
    suspend fun futurePendingSlots(now: Long = System.currentTimeMillis()): List<Pair<Slot, String>> {
        return slotDao.getAll()
            .filter { (it.status == SlotStatus.PENDING || it.status == SlotStatus.POSTPONED) && it.startTime > now }
            .mapNotNull { slot ->
                val task = taskDao.getById(slot.taskId) ?: return@mapNotNull null
                slot to task.name
            }
    }

    // ---- Slot generation ---------------------------------------------------

    /**
     * Adds auto-generated slots until planned hours cover the task duration.
     * New slots are placed one per day at the task's preferred hour, starting the
     * next day that is still in the future, skipping days that already have a slot.
     * Returns only the newly created slots.
     */
    private suspend fun generateMissingSlots(task: Task, existing: List<Slot>): List<Slot> {
        val planned = existing.count { it.countsTowardPlan } * Slot.SLOT_HOURS
        var shortfall = task.durationHours - planned
        if (shortfall <= 0) return emptyList()

        val zone = ZoneId.systemDefault()
        val time = LocalTime.of(task.startHour.coerceIn(0, 23), 0)
        val usedDates = existing.map {
            Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate()
        }.toMutableSet()

        var date = LocalDate.now(zone).plusDays(1)
        val created = mutableListOf<Slot>()
        var guard = 0
        while (shortfall > 0 && guard < 365) {
            guard++
            if (date in usedDates) { date = date.plusDays(1); continue }
            val startMillis = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
            if (startMillis <= System.currentTimeMillis()) { date = date.plusDays(1); continue }
            val id = slotDao.insert(Slot(taskId = task.id, startTime = startMillis))
            created += slotDao.getById(id)!!
            usedDates += date
            shortfall -= Slot.SLOT_HOURS
            date = date.plusDays(1)
        }
        return created
    }
}
