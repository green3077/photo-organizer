package com.green3077.photoorganizer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.green3077.photoorganizer.databinding.ItemLocationGroupBinding
import com.green3077.photoorganizer.model.LocationGroup

class LocationGroupAdapter(
    private val onClick: (LocationGroup) -> Unit
) : RecyclerView.Adapter<LocationGroupAdapter.ViewHolder>() {

    private val items = mutableListOf<LocationGroup>()

    fun submit(groups: List<LocationGroup>) {
        items.clear()
        items.addAll(groups)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLocationGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    /** 스크롤바 말풍선용 — "지역별 정리"의 여행 라벨처럼 긴 문자열은 잘라서 보여준다. */
    fun labelAt(position: Int): String? =
        items.getOrNull(position)?.placeName?.let { if (it.length > LABEL_MAX_LENGTH) it.take(LABEL_MAX_LENGTH) + "…" else it }

    inner class ViewHolder(private val binding: ItemLocationGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(group: LocationGroup) {
            binding.image.load(group.coverPhoto.uri) { crossfade(true) }
            binding.textTitle.text = group.placeName
            binding.textSummary.text = "${group.photoCount}장"
            binding.root.setOnClickListener { onClick(group) }
        }
    }

    private companion object {
        const val LABEL_MAX_LENGTH = 10
    }
}
