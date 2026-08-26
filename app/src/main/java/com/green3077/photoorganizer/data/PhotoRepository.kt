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
     * "일(day of month)"이 같은 사진을 월/연도에 상관없이 한데 묶는다 (예: 15일 → 1월15일, 3월15일, 작년 7월15일 ... 모두 한 그룹).
     * 실제로 같은 날짜가 두 번 이상 존재하는 그룹만 "반복되는 날"로 추려서 보여준다.
     * today를 기준으로 다음 기념일까지 가장 가까운 순으로 정렬.
     */
    fun buildRecurringMemoryGroups(photos: List<Photo>, today: LocalDate): List<MemoryGroup> {
        return photos
            .groupBy { it.dateTaken.dayOfMonth }
            .filterValues { group -> group.map { it.dateTaken }.distinct().size >= 2 }
            .map { (day, group) ->
                MemoryGroup(day, group.groupBy { it.dateTaken.year }.toSortedMap(compareByDescending { it }))
            }
            .sortedBy { daysUntilNextOccurrence(it.day, today) }
    }

    fun photosForExactDate(photos: List<Photo>, date: LocalDate): List<Photo> =
        photos.filter { it.dateTaken == date }

    /** 매달/매년 상관없이 그 "일(day of month)"에 찍힌 모든 사진을, 실제 촬영 날짜별로 묶어서 돌려준다. */
    fun photosForDay(photos: List<Photo>, day: Int): Map<LocalDate, List<Photo>> {
        return photos
            .filter { it.dateTaken.dayOfMonth == day }
            .groupBy { it.dateTaken }
            .toSortedMap(compareByDescending { it })
    }

    private fun daysUntilNextOccurrence(day: Int, today: LocalDate): Long {
        var cursor = today
        repeat(24) {
            if (day <= cursor.lengthOfMonth()) {
                val occurrence = cursor.withDayOfMonth(day)
                if (!occurrence.isBefore(today)) return ChronoUnit.DAYS.between(today, occurrence)
            }
            cursor = cursor.plusMonths(1).withDayOfMonth(1)
        }
        return Long.MAX_VALUE
    }
}
