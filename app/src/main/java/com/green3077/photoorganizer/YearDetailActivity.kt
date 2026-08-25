package com.green3077.photoorganizer

import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.util.DateFormat
import java.time.LocalDate
import java.time.MonthDay

/**
 * 연도 하나를 골라 그 해에 찍힌 모든 사진을 날짜별로 훑어보며 정리한다.
 * 홈 화면의 "날짜별" 탭은 여러 해에 걸쳐 반복되는 월/일만 추리는 반면,
 * 여기서는 그 연도 안의 모든 촬영일을 다 보여준다.
 */
class YearDetailActivity : BasePhotoDetailActivity() {

    private var year: Int = -1

    override fun parseExtras(): Boolean {
        year = intent.getIntExtra(EXTRA_YEAR, -1)
        return year != -1
    }

    override fun screenTitle(): String = getString(R.string.title_year_detail, year)

    override suspend fun loadPhotosByDate(): Map<LocalDate, List<Photo>> =
        repository.loadAllPhotos()
            .filter { it.dateTaken.year == year }
            .groupBy { it.dateTaken }
            .toSortedMap(compareByDescending { it })

    override fun sectionLabel(date: LocalDate): String = DateFormat.monthDayLabel(MonthDay.from(date))

    companion object {
        const val EXTRA_YEAR = "extra_year"
    }
}
