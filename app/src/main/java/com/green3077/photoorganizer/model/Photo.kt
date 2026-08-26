package com.green3077.photoorganizer.model

import android.net.Uri
import java.time.LocalDate

data class Photo(
    val id: Long,
    val uri: Uri,
    val dateTaken: LocalDate,
    val displayName: String,
    val isVideo: Boolean = false
)

data class MemoryGroup(
    val day: Int,
    val photosByYear: Map<Int, List<Photo>>
) {
    val photoCount: Int get() = photosByYear.values.sumOf { it.size }
    val dateCount: Int get() = photosByYear.values.flatten().map { it.dateTaken }.distinct().size
    val coverPhoto: Photo get() = photosByYear.entries.maxBy { it.key }.value.first()
}
