package com.jengadirect.scheduler.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jengadirect.scheduler.R
import com.jengadirect.scheduler.alarm.AlarmScheduler
import com.jengadirect.scheduler.alarm.Notifications
import com.jengadirect.scheduler.data.Repository
import com.jengadirect.scheduler.data.Slot
import com.jengadirect.scheduler.databinding.ActivityTaskDetailBinding
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class TaskDetailActivity : AppCompatActivity() {

    private lateinit var b: ActivityTaskDetailBinding
    private lateinit var repo: Repository
    private lateinit var adapter: SlotListAdapter
    private var taskId: Long = 0L
    private var pendingRescheduleId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTaskDetailBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repo = Repository(this)
        taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        if (taskId == 0L) {
            finish()
            return
        }
        pendingRescheduleId = intent.getLongExtra(EXTRA_RESCHEDULE_SLOT_ID, 0L)

        adapter = SlotListAdapter(
            onConfirm = { slot -> confirm(slot) },
            onReschedule = { slot -> pickDateTime(slot.startTime) { reschedule(slot, it) } },
            onRemove = { slot -> confirmRemove(slot) }
        )
        b.slotList.layoutManager = LinearLayoutManager(this)
        b.slotList.adapter = adapter

        b.btnAddSlot.setOnClickListener {
            val base = System.currentTimeMillis() + 24 * 3_600_000L
            pickDateTime(base) { addSlot(it) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRescheduleId = intent.getLongExtra(EXTRA_RESCHEDULE_SLOT_ID, 0L)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            repo.markPastSlotsMissed()
            val tp = repo.progressFor(taskId)
            if (tp == null) {
                finish()
                return@launch
            }
            title = tp.task.name
            b.detailProgress.text = getString(
                R.string.progress_fmt, tp.confirmedHours, tp.task.durationHours, tp.plannedHours
            )
            val hint = when {
                tp.isComplete -> getString(R.string.task_complete)
                tp.missingHours > 0 -> getString(R.string.need_more_slots_fmt, tp.missingHours)
                else -> ""
            }
            b.detailHint.text = hint
            b.detailHint.visibility = if (hint.isEmpty()) View.GONE else View.VISIBLE
            adapter.submit(tp.slots)
            Notifications.showNextUp(this@TaskDetailActivity, repo.nextUpcoming())

            if (pendingRescheduleId != 0L) {
                val target = tp.slots.find { it.id == pendingRescheduleId }
                pendingRescheduleId = 0L
                if (target != null) {
                    pickDateTime(target.startTime) { reschedule(target, it) }
                }
            }
        }
    }

    private fun addSlot(startMillis: Long) {
        lifecycleScope.launch {
            val slot = repo.addSlot(taskId, startMillis)
            val name = repo.getTask(taskId)?.name.orEmpty()
            AlarmScheduler.schedule(this@TaskDetailActivity, slot, name)
            Notifications.showNextUp(this@TaskDetailActivity, repo.nextUpcoming())
            refresh()
        }
    }

    private fun reschedule(slot: Slot, newStart: Long) {
        lifecycleScope.launch {
            AlarmScheduler.cancel(this@TaskDetailActivity, slot.id)
            val updated = repo.rescheduleSlot(slot.id, newStart)
            val name = repo.getTask(taskId)?.name.orEmpty()
            if (updated != null) {
                AlarmScheduler.schedule(this@TaskDetailActivity, updated, name)
            }
            Notifications.showNextUp(this@TaskDetailActivity, repo.nextUpcoming())
            refresh()
        }
    }

    private fun confirm(slot: Slot) {
        lifecycleScope.launch {
            repo.confirmSlot(slot.id)
            AlarmScheduler.cancel(this@TaskDetailActivity, slot.id)
            Notifications.showNextUp(this@TaskDetailActivity, repo.nextUpcoming())
            refresh()
        }
    }

    private fun confirmRemove(slot: Slot) {
        AlertDialog.Builder(this)
            .setMessage(R.string.remove_slot_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove_slot) { _, _ ->
                lifecycleScope.launch {
                    AlarmScheduler.cancel(this@TaskDetailActivity, slot.id)
                    repo.removeSlot(slot.id)
                    Notifications.showNextUp(this@TaskDetailActivity, repo.nextUpcoming())
                    refresh()
                }
            }
            .show()
    }

    private fun pickDateTime(baseMillis: Long, onPicked: (Long) -> Unit) {
        val zone = ZoneId.systemDefault()
        val base = Instant.ofEpochMilli(baseMillis).atZone(zone)
        DatePickerDialog(
            this,
            { _, year, month, day ->
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        val picked = ZonedDateTime.of(year, month + 1, day, hour, minute, 0, 0, zone)
                        onPicked(picked.toInstant().toEpochMilli())
                    },
                    base.hour, base.minute, true
                ).show()
            },
            base.year, base.monthValue - 1, base.dayOfMonth
        ).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_RESCHEDULE_SLOT_ID = "reschedule_slot_id"

        fun intent(context: Context, taskId: Long): Intent =
            Intent(context, TaskDetailActivity::class.java)
                .putExtra(EXTRA_TASK_ID, taskId)

        fun rescheduleIntent(context: Context, taskId: Long, slotId: Long): Intent =
            Intent(context, TaskDetailActivity::class.java)
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_RESCHEDULE_SLOT_ID, slotId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
