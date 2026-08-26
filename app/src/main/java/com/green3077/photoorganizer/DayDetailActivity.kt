package com.green3077.photoorganizer

import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.util.DateFormat
import java.time.LocalDate

/** 달력 화면에서 특정 날짜 칸을 눌렀을 때, 그 날짜에 찍은 사진만 모아 보여준다. */
class DayDetailActivity : BasePhotoDetailActivity() {

    private lateinit var date: LocalDate

    override fun parseExtras(): Boolean {
        val epochDay = intent.getLongExtra(EXTRA_EPOCH_DAY, Long.MIN_VALUE)
        if (epochDay == Long.MIN_VALUE) return false
        date = LocalDate.ofEpochDay(epochDay)
        return true
    }

    override fun screenTitle(): String = DateFormat.fullDateLabel(date)

    override suspend fun loadPhotosByDate(): Map<LocalDate, List<Photo>> {
        val allPhotos = repository.loadAllPhotos()
        val dayPhotos = repository.photosForExactDate(allPhotos, date)
        return if (dayPhotos.isEmpty()) emptyMap() else mapOf(date to dayPhotos)
    }

    override fun sectionLabel(date: LocalDate): String = DateFormat.fullDateLabel(date)

    companion object {
        const val EXTRA_EPOCH_DAY = "extra_epoch_day"
    }
}
