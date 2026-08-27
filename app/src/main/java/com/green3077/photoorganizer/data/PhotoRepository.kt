package com.green3077.photoorganizer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import com.green3077.photoorganizer.model.MemoryGroup
import com.green3077.photoorganizer.model.MonthGroup
import com.green3077.photoorganizer.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PhotoRepository(private val context: Context) {

    suspend fun loadAllPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        val photos = queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false) +
            queryMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true)
        photos.sortedByDescending { it.dateTaken }
    }

    /** 휴지통(시스템 IS_TRASHED)으로 보내진 사진/동영상만 불러온다. */
    suspend fun loadTrashedPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        val photos = queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false, trashedOnly = true) +
            queryMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true, trashedOnly = true)
        photos.sortedByDescending { it.dateTaken }
    }

    private fun queryMedia(collection: Uri, isVideo: Boolean, trashedOnly: Boolean = false): List<Photo> {
        val photos = mutableListOf<Photo>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_TAKEN} DESC"
        val zone = ZoneId.systemDefault()

        val cursor = if (trashedOnly) {
            val queryArgs = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
            }
            context.contentResolver.query(collection, projection, queryArgs, null)
        } else {
            context.contentResolver.query(collection, projection, null, null, sortOrder)
        }

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dateTakenCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val dateTakenMillis = it.getLong(dateTakenCol)
                val dateAddedSeconds = it.getLong(dateAddedCol)
                val millis = if (dateTakenMillis > 0) dateTakenMillis else dateAddedSeconds * 1000
                if (millis <= 0) continue

                val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                val uri = ContentUris.withAppendedId(collection, id)
                photos.add(Photo(id, uri, date, it.getString(nameCol) ?: "", isVideo))
            }
        }
        return photos
    }

    /**
     * "일(day of month)"이 같은 사진을 월/연도에 상관없이 한데 묶는다 (예: 15일 → 1월15일, 3월15일, 작년 7월15일 ... 모두 한 그룹).
     * 실제로 같은 날짜가 두 번 이상 존재하는 그룹만 "반복되는 날"로 추려서 보여준다.
     * 1일부터 31일까지 순서대로 정렬.
     */
    fun buildRecurringMemoryGroups(photos: List<Photo>): List<MemoryGroup> {
        return photos
            .groupBy { it.dateTaken.dayOfMonth }
            .filterValues { group -> group.map { it.dateTaken }.distinct().size >= 2 }
            .map { (day, group) ->
                MemoryGroup(day, group.groupBy { it.dateTaken.year }.toSortedMap(compareByDescending { it }))
            }
            .sortedBy { it.day }
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

    /** 연도에 상관없이 "월(1~12월)" 단위로 사진을 모은다. 사진이 있는 달만 1월부터 순서대로. */
    fun buildMonthGroups(photos: List<Photo>): List<MonthGroup> {
        return photos
            .groupBy { it.dateTaken.monthValue }
            .map { (month, group) ->
                MonthGroup(month, group.groupBy { it.dateTaken.year }.toSortedMap(compareByDescending { it }))
            }
            .sortedBy { it.month }
    }

    /** 연도에 상관없이 그 "월"에 찍힌 모든 사진을, 실제 촬영 날짜별로 묶어서 돌려준다. */
    fun photosForMonth(photos: List<Photo>, month: Int): Map<LocalDate, List<Photo>> {
        return photos
            .filter { it.dateTaken.monthValue == month }
            .groupBy { it.dateTaken }
            .toSortedMap(compareByDescending { it })
    }
}
