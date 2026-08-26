package com.green3077.photoorganizer.model

import android.net.Uri
import java.time.LocalDate

data class Photo(
    val id: Long,
    val uri: Uri,
    val dateTaken: LocalDate,
    val displayName: String
)

data class MemoryGroup(
    val dayOfMonth: Int,
    val photosByYear: Map<Int, List<Photo>>
) {
    val yearCount: Int get() = photosByYear.size
    val photoCount: Int get() = photosByYear.values.sumOf { it.size }
    val coverPhoto: Photo get() = photosByYear.entries.maxBy { it.key }.value.first()
}
