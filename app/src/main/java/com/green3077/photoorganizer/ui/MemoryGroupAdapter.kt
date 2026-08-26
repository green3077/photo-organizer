package com.green3077.photoorganizer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.green3077.photoorganizer.databinding.ItemMemoryGroupBinding
import com.green3077.photoorganizer.databinding.ItemMemoryGroupGridBinding
import com.green3077.photoorganizer.model.MemoryGroup
import com.green3077.photoorganizer.util.DateFormat

enum class GroupViewMode { LIST, GRID }

class MemoryGroupAdapter(
    private var viewMode: GroupViewMode,
    private val onClick: (MemoryGroup) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<MemoryGroup>()

    fun submit(groups: List<MemoryGroup>) {
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

    inner class ListViewHolder(private val binding: ItemMemoryGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(group: MemoryGroup) {
            binding.image.load(group.coverPhoto.uri) { crossfade(true) }
            binding.textDate.text = DateFormat.dayLabel(group.day)
            binding.textSummary.text = "${group.dateCount}개 날짜 · 총 ${group.photoCount}장"
            binding.root.setOnClickListener { onClick(group) }
        }
    }

    inner class GridViewHolder(private val binding: ItemMemoryGroupGridBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(group: MemoryGroup) {
            binding.image.load(group.coverPhoto.uri) { crossfade(true) }
            binding.textDate.text = DateFormat.dayLabel(group.day)
            binding.textCount.text = "${group.photoCount}장"
            binding.root.setOnClickListener { onClick(group) }
        }
    }

    companion object {
        private const val TYPE_LIST = 0
        private const val TYPE_GRID = 1
    }
}
