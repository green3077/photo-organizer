package com.green3077.photoorganizer

import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.util.DateFormat
import java.time.LocalDate

/**
 * 특정 "일(day of month)"을 열어, 월/연도에 상관없이 그 날짜에 찍힌 사진을 모두 보여준다
 * (홈 화면 "날짜별" 탭의 상세 화면). 예: 15일 → 1월15일, 3월15일, 작년 7월15일 사진이 한 화면에 모인다.
 */
class DetailActivity : BasePhotoDetailActivity() {

    private var day: Int = -1

    override fun parseExtras(): Boolean {
        day = intent.getIntExtra(EXTRA_DAY, -1)
        return day in 1..31
    }

    override fun screenTitle(): String = DateFormat.dayLabel(day)

    override suspend fun loadPhotosByDate(): Map<LocalDate, List<Photo>> {
        val allPhotos = repository.loadAllPhotos()
        return repository.photosForDay(allPhotos, day)
    }

    override fun sectionLabel(date: LocalDate): String = DateFormat.fullDateLabel(date)

    companion object {
        const val EXTRA_DAY = "extra_day"
    }
}
