package com.green3077.photoorganizer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.green3077.photoorganizer.databinding.ItemMemoryGroupBinding
import com.green3077.photoorganizer.databinding.ItemMemoryGroupGridBinding
import com.green3077.photoorganizer.model.MonthGroup
import com.green3077.photoorganizer.util.DateFormat

class MonthGroupAdapter(
    private var viewMode: GroupViewMode,
    private val onClick: (MonthGroup) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<MonthGroup>()

    fun submit(groups: List<MonthGroup>) {
        items.clear()
        items.addAll(groups)
        notifyDataSetChanged()
    }

    fun setViewMode(mode: GroupViewMode) {
        if (viewMode == mode) return
        viewMode = mode
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = if (viewMode == GroupViewMode.GRID) TYPE_GRID else TYPE_LIST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GRID) {
            GridViewHolder(ItemMemoryGroupGridBinding.inflate(inflater, parent, false))
        } else {
            ListViewHolder(ItemMemoryGroupBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val group = items[position]
        when (holder) {
            is GridViewHolder -> holder.bind(group)
            is ListViewHolder -> holder.bind(group)
        }
    }

    override fun getItemCount() = items.size

    fun labelAt(position: Int): String? = items.getOrNull(position)?.let { DateFormat.monthLabel(it.month) }

    inner class ListViewHolder(private val binding: ItemMemoryGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(group: MonthGroup) {
            binding.image.load(group.coverPhoto.uri) { crossfade(true) }
            binding.textDate.text = DateFormat.monthLabel(group.month)
            val breakdown = if (group.videoCount > 0) " (사진 ${group.imageCount}장, 동영상 ${group.videoCount}개)" else ""
            binding.textSummary.text = "${group.dateCount}개 날짜 · 총 ${group.photoCount}장$breakdown"
            binding.root.setOnClickListener { onClick(group) }
        }
    }

    inner class GridViewHolder(private val binding: ItemMemoryGroupGridBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(group: MonthGroup) {
            binding.image.load(group.coverPhoto.uri) { crossfade(true) }
            binding.textDate.text = DateFormat.monthLabel(group.month)
            binding.textCount.text = "${group.photoCount}장"
            binding.root.setOnClickListener { onClick(group) }
        }
    }

    companion object {
        private const val TYPE_LIST = 0
        private const val TYPE_GRID = 1
    }
}
