package com.jengadirect.scheduler.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/** Total work item, e.g. "Outlier application", 8 hrs. Worked through in fixed 4 hr slots. */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val durationHours: Int,
    /** Preferred hour of day (0-23) used when auto-generating slots. */
    val startHour: Int = 9,
    val createdAt: Long = System.currentTimeMillis()
)

enum class SlotStatus { PENDING, CONFIRMED, POSTPONED, MISSED }

/** A single fixed-length (4 hr) block of time scheduled against a task. */
@Entity(
    tableName = "slots",
    foreignKeys = [ForeignKey(
        entity = Task::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taskId")]
)
data class Slot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    /** Epoch millis of the slot start. */
    val startTime: Long,
    val durationHours: Int = SLOT_HOURS,
    val status: SlotStatus = SlotStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
) {
    val endTime: Long get() = startTime + durationHours * 3_600_000L

    /** True while this slot still counts toward the task plan. */
    val countsTowardPlan: Boolean get() = status != SlotStatus.MISSED

    companion object {
        const val SLOT_HOURS = 4
    }
}

class Converters {
    @TypeConverter fun fromStatus(value: SlotStatus): String = value.name
    @TypeConverter fun toStatus(value: String): SlotStatus =
        runCatching { SlotStatus.valueOf(value) }.getOrDefault(SlotStatus.PENDING)
}
