package com.green3077.photoorganizer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.green3077.photoorganizer.R
import com.green3077.photoorganizer.databinding.ItemPhotoBinding
import com.green3077.photoorganizer.databinding.ItemSectionHeaderBinding
import com.green3077.photoorganizer.model.Photo

sealed class DetailRow {
    data class SectionHeader(val label: String, val count: Int) : DetailRow()
    data class PhotoRow(val photo: Photo) : DetailRow()
}

/**
 * 연도별(날짜 상세)이든 날짜별(장소 상세)이든, 문자열 라벨을 키로 하는 섹션 목록을 그린다.
 * 모든 사진에 체크박스를 항상 보여줘서 탭 한 번으로 선택할 수 있고, 길게 눌러 손을 떼지
 * 않은 채 드래그하면 지나가는 사진들이 잇달아 선택된다(DragSelectTouchListener가 처리).
 */
class DetailListAdapter(
    private val isSelected: (Long) -> Boolean,
    private val onPhotoClick: (Photo) -> Unit,
    private val onToggleSelect: (Photo) -> Unit,
    private val onToggleSection: (photos: List<Photo>, selected: Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<DetailRow>()

    fun submit(sections: Map<String, List<Photo>>) {
        items.clear()
        for ((label, photos) in sections) {
            items.add(DetailRow.SectionHeader(label, photos.size))
            photos.forEach { items.add(DetailRow.PhotoRow(it)) }
        }
        notifyDataSetChanged()
    }

    fun refreshSelectionState() {
        notifyDataSetChanged()
    }

    fun isHeaderAt(position: Int): Boolean = items[position] is DetailRow.SectionHeader

    fun photoAt(position: Int): Photo? = (items.getOrNull(position) as? DetailRow.PhotoRow)?.photo

    /** 이 position 바로 위(포함)에서 가장 가까운 섹션 헤더의 라벨(연도/날짜 등)을 찾는다. */
    fun labelAt(position: Int): String? {
        for (i in position downTo 0) {
            (items.getOrNull(i) as? DetailRow.SectionHeader)?.let { return it.label }
        }
        return null
    }

    /** 헤더 바로 다음부터 다음 헤더(혹은 끝) 전까지, 이 섹션에 속한 사진들. */
    private fun photosInSectionAt(headerPosition: Int): List<Photo> {
        val photos = mutableListOf<Photo>()
        for (i in headerPosition + 1 until items.size) {
            val row = items[i]
            if (row is DetailRow.SectionHeader) break
            if (row is DetailRow.PhotoRow) photos.add(row.photo)
        }
        return photos
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is DetailRow.SectionHeader -> TYPE_HEADER
        is DetailRow.PhotoRow -> TYPE_PHOTO
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(ItemSectionHeaderBinding.inflate(inflater, parent, false))
        } else {
            PhotoViewHolder(ItemPhotoBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = items[position]) {
            is DetailRow.SectionHeader -> (holder as HeaderViewHolder).bind(row)
            is DetailRow.PhotoRow -> (holder as PhotoViewHolder).bind(row.photo)
        }
    }

    inner class HeaderViewHolder(private val binding: ItemSectionHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: DetailRow.SectionHeader) {
            binding.textLabel.text = header.label
            binding.textCount.text = "${header.count}장"

            val position = bindingAdapterPosition
            val photos = if (position != RecyclerView.NO_POSITION) photosInSectionAt(position) else emptyList()
            val allSelected = photos.isNotEmpty() && photos.all { isSelected(it.id) }
            binding.checkIcon.setImageResource(
                if (allSelected) R.drawable.ic_check_circle_filled else R.drawable.ic_check_circle_outline
            )
            binding.checkIcon.setOnClickListener { onToggleSection(photos, !allSelected) }
        }
    }

    inner class PhotoViewHolder(private val binding: ItemPhotoBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(photo: Photo) {
            binding.image.load(photo.uri) { crossfade(true) }
            binding.videoBadge.visibility = if (photo.isVideo) View.VISIBLE else View.GONE
            val selected = isSelected(photo.id)
            binding.checkOverlay.visibility = if (selected) View.VISIBLE else View.GONE
            binding.checkIcon.setImageResource(
                if (selected) R.drawable.ic_check_circle_filled else R.drawable.ic_check_circle_outline
            )
            binding.checkIcon.setOnClickListener { onToggleSelect(photo) }
            binding.root.setOnClickListener {
                if (isSelected(photo.id) || hasAnySelected()) onToggleSelect(photo) else onPhotoClick(photo)
            }
        }

        private fun hasAnySelected(): Boolean =
            items.any { it is DetailRow.PhotoRow && isSelected(it.photo.id) }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_PHOTO = 1
    }
}
