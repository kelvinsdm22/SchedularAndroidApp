package com.jengadirect.scheduler.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jengadirect.scheduler.R
import com.jengadirect.scheduler.alarm.AlarmScheduler
import com.jengadirect.scheduler.alarm.Notifications
import com.jengadirect.scheduler.data.Repository
import com.jengadirect.scheduler.data.SlotStatus
import com.jengadirect.scheduler.databinding.ActivityTaskEditBinding
import kotlinx.coroutines.launch

class TaskEditActivity : AppCompatActivity() {

    private lateinit var b: ActivityTaskEditBinding
    private lateinit var repo: Repository
    private var taskId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTaskEditBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repo = Repository(this)
        taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)

        if (taskId != 0L) {
            title = getString(R.string.edit_task)
            b.btnDelete.visibility = android.view.View.VISIBLE
            loadTask()
        } else {
            title = getString(R.string.add_task)
            b.inputStartHour.setText("9")
        }

        b.btnSave.setOnClickListener { save() }
        b.btnDelete.setOnClickListener { confirmDelete() }
    }

    private fun loadTask() {
        lifecycleScope.launch {
            repo.getTask(taskId)?.let { t ->
                b.inputName.setText(t.name)
                b.inputDuration.setText(t.durationHours.toString())
                b.inputStartHour.setText(t.startHour.toString())
            }
        }
    }

    private fun save() {
        val name = b.inputName.text?.toString()?.trim().orEmpty()
        val duration = b.inputDuration.text?.toString()?.toIntOrNull() ?: 0
        val startHour = (b.inputStartHour.text?.toString()?.toIntOrNull() ?: 9).coerceIn(0, 23)

        if (name.isEmpty()) {
            b.inputName.error = getString(R.string.task_name)
            return
        }
        if (duration <= 0) {
            b.inputDuration.error = getString(R.string.duration_hours)
            return
        }

        lifecycleScope.launch {
            if (taskId == 0L) {
                val id = repo.createTaskWithSlots(name, duration, startHour)
                repo.slotsForTask(id)
                    .filter { it.status == SlotStatus.PENDING }
                    .forEach { AlarmScheduler.schedule(this@TaskEditActivity, it, name) }
            } else {
                val current = repo.getTask(taskId) ?: return@launch
                val updated = current.copy(
                    name = name, durationHours = duration, startHour = startHour
                )
                repo.updateTask(updated).forEach {
                    AlarmScheduler.schedule(this@TaskEditActivity, it, name)
                }
            }
            Notifications.showNextUp(this@TaskEditActivity, repo.nextUpcoming())
            finish()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_task_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    repo.slotsForTask(taskId).forEach {
                        AlarmScheduler.cancel(this@TaskEditActivity, it.id)
                    }
                    repo.deleteTask(taskId)
                    Notifications.showNextUp(this@TaskEditActivity, repo.nextUpcoming())
                    finish()
                }
            }
            .show()
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
    }
}
