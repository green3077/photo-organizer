package com.green3077.photoorganizer

import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.util.DateFormat
import java.time.LocalDate

/**
 * 특정 며칠(dayOfMonth)을 열어, 월과 관계없이 그 날짜에 찍힌 사진을 모두 보여준다
 * (홈 화면 "날짜별" 탭의 상세 화면). 예: "15일"을 열면 1/15, 3/15, 12/15 등 모든
 * 월·연도의 15일 사진이 함께 나온다.
 */
class DetailActivity : BasePhotoDetailActivity() {

    private var day: Int = -1

    override fun parseExtras(): Boolean {
        day = intent.getIntExtra(EXTRA_DAY, -1)
        return day != -1
    }

    override fun screenTitle(): String = DateFormat.dayOfMonthLabel(day)

    override suspend fun loadPhotosByDate(): Map<LocalDate, List<Photo>> {
        val allPhotos = repository.loadAllPhotos()
        return repository.photosForDayOfMonth(allPhotos, day)
    }

    override fun sectionLabel(date: LocalDate): String = DateFormat.fullDateLabel(date)

    companion object {
        const val EXTRA_DAY = "extra_day"
    }
}
