package com.green3077.photoorganizer

import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.util.DateFormat
import java.time.LocalDate

/**
 * 특정 "월"을 열어, 연도에 상관없이 그 달에 찍힌 사진을 모두 보여준다("월별 정리"의 상세 화면).
 * 예: 8월 → 2023년 8월, 2024년 8월, 2025년 8월 사진이 한 화면에 모인다.
 */
class MonthDetailActivity : BasePhotoDetailActivity() {

    private var month: Int = -1

    override fun parseExtras(): Boolean {
        month = intent.getIntExtra(EXTRA_MONTH, -1)
        return month in 1..12
    }

    override fun screenTitle(): String = DateFormat.monthLabel(month)

    override suspend fun loadPhotosByDate(): Map<LocalDate, List<Photo>> {
        val allPhotos = repository.loadAllPhotos()
        return repository.photosForMonth(allPhotos, month)
    }

    override fun sectionLabel(date: LocalDate): String = DateFormat.fullDateLabel(date)

    companion object {
        const val EXTRA_MONTH = "extra_month"
    }
}
