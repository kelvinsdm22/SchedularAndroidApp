package com.jengadirect.scheduler.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jengadirect.scheduler.data.Slot
import com.jengadirect.scheduler.data.SlotStatus
import com.jengadirect.scheduler.databinding.ItemSlotBinding

class SlotListAdapter(
    private val onConfirm: (Slot) -> Unit,
    private val onReschedule: (Slot) -> Unit,
    private val onRemove: (Slot) -> Unit
) : RecyclerView.Adapter<SlotListAdapter.VH>() {

    private val items = mutableListOf<Slot>()

    fun submit(list: List<Slot>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSlotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val b: ItemSlotBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(slot: Slot) {
            val ctx = b.root.context
            b.slotWhen.text = Format.day(slot.startTime)
            b.slotRange.text = "${Format.time(slot.startTime)} – ${Format.time(slot.endTime)}"
            b.slotStatus.text = Format.statusLabel(ctx, slot.status)
            b.slotStatus.setBackgroundColor(
                ContextCompat.getColor(ctx, Format.statusColor(slot.status))
            )
            b.btnConfirm.isEnabled = slot.status != SlotStatus.CONFIRMED
            b.btnConfirm.setOnClickListener { onConfirm(slot) }
            b.btnReschedule.setOnClickListener { onReschedule(slot) }
            b.btnRemove.setOnClickListener { onRemove(slot) }
        }
    }
}
