package com.green3077.photoorganizer.model

/** "월별 정리" 화면에서 쓰는 그룹 — 연도에 상관없이 그 달(1~12월)에 찍힌 모든 사진. */
data class MonthGroup(
    val month: Int,
    val photosByYear: Map<Int, List<Photo>>
) {
    val photoCount: Int get() = photosByYear.values.sumOf { it.size }
    val imageCount: Int get() = photosByYear.values.sumOf { list -> list.count { !it.isVideo } }
    val videoCount: Int get() = photosByYear.values.sumOf { list -> list.count { it.isVideo } }
    val dateCount: Int get() = photosByYear.values.flatten().map { it.dateTaken }.distinct().size
    val coverPhoto: Photo get() = photosByYear.entries.maxBy { it.key }.value.first()
}
