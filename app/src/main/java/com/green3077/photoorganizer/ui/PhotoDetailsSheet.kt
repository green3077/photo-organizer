package com.green3077.photoorganizer.ui

import android.content.Context
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.green3077.photoorganizer.R
import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.model.PhotoDetails
import com.green3077.photoorganizer.util.DateFormat

/** 갤럭시 갤러리의 "상세 정보"처럼, 파일명/용량/해상도/촬영 위치 등을 바텀시트로 보여준다. */
object PhotoDetailsSheet {

    fun show(context: Context, photo: Photo, details: PhotoDetails) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.bottomsheet_photo_details, null)
        val container = view.findViewById<LinearLayout>(R.id.rowsContainer)

        fun addRow(label: String, value: String) {
            val row = inflater.inflate(R.layout.item_detail_row, container, false)
            row.findViewById<TextView>(R.id.label).text = label
            row.findViewById<TextView>(R.id.value).text = value
            container.addView(row)
        }

        addRow(context.getString(R.string.detail_file_name), details.displayName)
        addRow(context.getString(R.string.detail_date_taken), DateFormat.fullDateLabel(photo.dateTaken))
        addRow(context.getString(R.string.detail_file_size), DateFormat.fileSize(details.sizeBytes))
        if (details.width > 0 && details.height > 0) {
            addRow(context.getString(R.string.detail_resolution), "${details.width} x ${details.height}")
        }
        details.durationMs?.let { addRow(context.getString(R.string.detail_duration), DateFormat.duration(it)) }
        addRow(
            context.getString(R.string.detail_location),
            details.locationLabel ?: context.getString(R.string.detail_location_unknown)
        )

        BottomSheetDialog(context).apply {
            setContentView(view)
            show()
        }
    }
}
