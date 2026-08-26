package com.green3077.photoorganizer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.green3077.photoorganizer.R
import com.green3077.photoorganizer.databinding.ItemCalendarDayBinding
import com.green3077.photoorganizer.model.CalendarCell
import java.time.LocalDate

class CalendarDayAdapter(
    private val onClick: (LocalDate) -> Unit
) : RecyclerView.Adapter<CalendarDayAdapter.ViewHolder>() {

    private val items = mutableListOf<CalendarCell>()

    fun submit(cells: List<CalendarCell>) {
        items.clear()
        items.addAll(cells)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemCalendarDayBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(cell: CalendarCell) {
            when (cell) {
                is CalendarCell.Blank -> {
                    binding.textDate.text = ""
                    binding.image.visibility = View.GONE
                    binding.scrimTop.visibility = View.GONE
                    binding.scrimBottom.visibility = View.GONE
                    binding.textCount.visibility = View.GONE
                    binding.root.setOnClickListener(null)
                    binding.root.isClickable = false
                }
                is CalendarCell.Day -> bind(cell)
            }
        }

        private fun bind(day: CalendarCell.Day) {
            binding.textDate.text = day.date.dayOfMonth.toString()
            val hasPhotos = day.cover != null

            binding.image.visibility = if (hasPhotos) View.VISIBLE else View.GONE
            binding.scrimTop.visibility = if (hasPhotos) View.VISIBLE else View.GONE
            binding.scrimBottom.visibility = if (hasPhotos) View.VISIBLE else View.GONE
            binding.textCount.visibility = if (hasPhotos) View.VISIBLE else View.GONE

            val context = binding.root.context
            binding.textDate.setTextColor(
                ContextCompat.getColor(context, if (hasPhotos) R.color.white else R.color.text_secondary)
            )

            if (hasPhotos) {
                binding.image.load(day.cover!!.uri) { crossfade(true) }
                binding.textCount.text = "${day.count}장"
                binding.root.isClickable = true
                binding.root.setOnClickListener { onClick(day.date) }
            } else {
                binding.root.isClickable = false
                binding.root.setOnClickListener(null)
            }
        }
    }
}
