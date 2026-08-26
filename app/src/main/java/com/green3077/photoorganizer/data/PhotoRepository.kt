package com.green3077.photoorganizer.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.green3077.photoorganizer.model.MemoryGroup
import com.green3077.photoorganizer.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class PhotoRepository(private val context: Context) {

    suspend fun loadAllPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Photo>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        val zone = ZoneId.systemDefault()

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val dateTakenMillis = cursor.getLong(dateTakenCol)
                val dateAddedSeconds = cursor.getLong(dateAddedCol)
                val millis = if (dateTakenMillis > 0) dateTakenMillis else dateAddedSeconds * 1000
                if (millis <= 0) continue

                val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                photos.add(Photo(id, uri, date, nameCol.let { cursor.getString(it) } ?: ""))
            }
        }
        photos
    }

    /**
     * "며칠(dayOfMonth)"이 2개 이상의 연도에 걸쳐 존재하는 그룹만 추림 ("매달 이 날" 후보).
     * 월은 구분하지 않으므로 1/15, 3/15, 12/15에 찍은 사진이 모두 "15일" 한 그룹으로 모인다.
     * today를 기준으로 다음 기념일까지 가장 가까운 순으로 정렬.
     */
    fun buildRecurringMemoryGroups(photos: List<Photo>, today: LocalDate): List<MemoryGroup> {
        return photos
            .groupBy { it.dateTaken.dayOfMonth }
            .filterValues { group -> group.map { it.dateTaken.year }.distinct().size >= 2 }
            .map { (day, group) ->
                MemoryGroup(day, group.groupBy { it.dateTaken.year }.toSortedMap(compareByDescending { it }))
            }
            .sortedBy { daysUntilNextOccurrence(it.dayOfMonth, today) }
    }

    fun photosForExactDate(photos: List<Photo>, date: LocalDate): List<Photo> =
        photos.filter { it.dateTaken == date }

    /** 월과 관계없이 며칠(dayOfMonth)만 같은 사진을 모아 정확한 촬영일(LocalDate)별로 묶는다. */
    fun photosForDayOfMonth(photos: List<Photo>, day: Int): Map<LocalDate, List<Photo>> {
        return photos
            .filter { it.dateTaken.dayOfMonth == day }
            .groupBy { it.dateTaken }
            .toSortedMap(compareByDescending { it })
    }

    /** today 이후(오늘 포함) 가장 가까운, 그 day가 실제로 존재하는 달까지 남은 일수. */
    private fun daysUntilNextOccurrence(day: Int, today: LocalDate): Long {
        for (monthsAhead in 0..12) {
            val yearMonth = YearMonth.from(today).plusMonths(monthsAhead.toLong())
            if (day > yearMonth.lengthOfMonth()) continue
            val candidate = yearMonth.atDay(day)
            if (!candidate.isBefore(today)) {
                return ChronoUnit.DAYS.between(today, candidate)
            }
        }
        return Long.MAX_VALUE
    }
}
