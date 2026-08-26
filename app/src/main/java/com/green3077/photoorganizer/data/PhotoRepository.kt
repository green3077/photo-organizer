package com.green3077.photoorganizer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.green3077.photoorganizer.model.MemoryGroup
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

    private fun queryMedia(collection: Uri, isVideo: Boolean): List<Photo> {
        val photos = mutableListOf<Photo>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_TAKEN} DESC"
        val zone = ZoneId.systemDefault()

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val dateTakenMillis = cursor.getLong(dateTakenCol)
                val dateAddedSeconds = cursor.getLong(dateAddedCol)
                val millis = if (dateTakenMillis > 0) dateTakenMillis else dateAddedSeconds * 1000
                if (millis <= 0) continue

                val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                val uri = ContentUris.withAppendedId(collection, id)
                photos.add(Photo(id, uri, date, cursor.getString(nameCol) ?: "", isVideo))
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
}
