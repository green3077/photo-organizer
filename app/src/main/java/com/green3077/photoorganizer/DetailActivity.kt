package com.green3077.photoorganizer

import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.util.DateFormat
import java.time.LocalDate
import java.time.MonthDay

/**
 * 특정 월/일을 열어 연도별로 묶인 사진을 보여준다 (홈 화면 "날짜별" 탭의 상세 화면).
 */
class DetailActivity : BasePhotoDetailActivity() {

    private lateinit var monthDay: MonthDay

    override fun parseExtras(): Boolean {
        val month = intent.getIntExtra(EXTRA_MONTH, -1)
        val day = intent.getIntExtra(EXTRA_DAY, -1)
        if (month == -1 || day == -1) return false
        monthDay = MonthDay.of(month, day)
        return true
    }

    override fun screenTitle(): String = DateFormat.monthDayLabel(monthDay)

    override suspend fun loadPhotosByDate(): Map<LocalDate, List<Photo>> {
        val allPhotos = repository.loadAllPhotos()
        return repository.photosForMonthDay(allPhotos, monthDay)
            .mapKeys { (_, photos) -> photos.first().dateTaken }
    }

    override fun sectionLabel(date: LocalDate): String = DateFormat.yearLabel(date.year, LocalDate.now())

    companion object {
        const val EXTRA_MONTH = "extra_month"
        const val EXTRA_DAY = "extra_day"
    }
}
