package com.jengadirect.scheduler.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface TaskDao {
    @Insert suspend fun insert(task: Task): Long
    @Update suspend fun update(task: Task)
    @Delete suspend fun delete(task: Task)

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    suspend fun getAll(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?
}

@Dao
interface SlotDao {
    @Insert suspend fun insert(slot: Slot): Long
    @Update suspend fun update(slot: Slot)
    @Delete suspend fun delete(slot: Slot)

    @Query("SELECT * FROM slots WHERE taskId = :taskId ORDER BY startTime ASC")
    suspend fun getForTask(taskId: Long): List<Slot>

    @Query("SELECT * FROM slots WHERE id = :id")
    suspend fun getById(id: Long): Slot?

    @Query("SELECT * FROM slots")
    suspend fun getAll(): List<Slot>

    @Query("UPDATE slots SET status = 'MISSED' WHERE status IN ('PENDING','POSTPONED') AND (startTime + durationHours * 3600000) < :now")
    suspend fun markMissedBefore(now: Long): Int
}
