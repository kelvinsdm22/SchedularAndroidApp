package com.jengadirect.scheduler.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jengadirect.scheduler.R
import com.jengadirect.scheduler.data.TaskProgress
import com.jengadirect.scheduler.databinding.ItemTaskBinding

class TaskListAdapter(
    private val onClick: (TaskProgress) -> Unit
) : RecyclerView.Adapter<TaskListAdapter.VH>() {

    private val items = mutableListOf<TaskProgress>()

    fun submit(list: List<TaskProgress>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val b: ItemTaskBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(tp: TaskProgress) {
            val ctx = b.root.context
            b.taskName.text = tp.task.name
            b.taskProgressText.text = ctx.getString(
                R.string.progress_fmt, tp.confirmedHours, tp.task.durationHours, tp.plannedHours
            )
            b.taskProgressBar.progress = tp.percent

            when {
                tp.isComplete -> {
                    b.taskHint.text = ctx.getString(R.string.task_complete)
                    b.taskHint.setTextColor(ContextCompat.getColor(ctx, R.color.status_confirmed))
                    b.taskHint.visibility = android.view.View.VISIBLE
                }
                tp.missingHours > 0 -> {
                    b.taskHint.text = ctx.getString(R.string.need_more_slots_fmt, tp.missingHours)
                    b.taskHint.setTextColor(ContextCompat.getColor(ctx, R.color.status_pending))
                    b.taskHint.visibility = android.view.View.VISIBLE
                }
                else -> b.taskHint.visibility = android.view.View.GONE
            }
            b.root.setOnClickListener { onClick(tp) }
        }
    }
}
