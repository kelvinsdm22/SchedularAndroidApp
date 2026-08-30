package com.jengadirect.scheduler.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.jengadirect.scheduler.Prefs
import com.jengadirect.scheduler.R
import com.jengadirect.scheduler.alarm.Notifications
import com.jengadirect.scheduler.data.Repository
import com.jengadirect.scheduler.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: Repository
    private lateinit var adapter: TaskListAdapter

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.tasks_title)

        repo = Repository(this)
        adapter = TaskListAdapter { tp ->
            startActivity(TaskDetailActivity.intent(this, tp.task.id))
        }
        binding.taskList.layoutManager = LinearLayoutManager(this)
        binding.taskList.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, TaskEditActivity::class.java))
        }

        maybeRequestNotificationPermission()
        maybePromptExactAlarm()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.action_toggle_next_up)?.isChecked = Prefs.nextUpEnabled(this)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_toggle_next_up) {
            val enabled = !item.isChecked
            item.isChecked = enabled
            Prefs.setNextUpEnabled(this, enabled)
            // Turning it back on should clear any stale "dismissed" marker.
            if (enabled) Prefs.setNextUpDismissedKey(this, null)
            lifecycleScope.launch {
                Notifications.showNextUp(this@MainActivity, repo.nextUpcoming())
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun refresh() {
        lifecycleScope.launch {
            repo.markPastSlotsMissed()
            val items = repo.allProgress()
            adapter.submit(items)
            binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            Notifications.showNextUp(this@MainActivity, repo.nextUpcoming())
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun maybePromptExactAlarm() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        if (am.canScheduleExactAlarms()) return
        Snackbar.make(binding.root, R.string.perm_exact_alarm_rationale, Snackbar.LENGTH_LONG)
            .setAction(R.string.allow) {
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }
            .show()
    }
}
