package com.green3077.photoorganizer.data

import android.content.Context
import android.location.Geocoder
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.model.PhotoDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** 갤러리 앱들의 "상세 정보"에 해당하는 값(용량, 해상도, 재생 시간, 촬영 위치)을 모아온다. */
class PhotoDetailsLoader(private val context: Context) {

    suspend fun load(photo: Photo): PhotoDetails = withContext(Dispatchers.IO) {
        var sizeBytes = 0L
        var width = 0
        var height = 0
        var durationMs: Long? = null

        val projection = mutableListOf(
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT
        )
        if (photo.isVideo) projection.add(MediaStore.Video.Media.DURATION)

        context.contentResolver.query(photo.uri, projection.toTypedArray(), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(MediaStore.MediaColumns.SIZE).takeIf { it >= 0 }
                    ?.let { sizeBytes = cursor.getLong(it) }
                cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH).takeIf { it >= 0 }
                    ?.let { width = cursor.getInt(it) }
                cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT).takeIf { it >= 0 }
                    ?.let { height = cursor.getInt(it) }
                if (photo.isVideo) {
                    cursor.getColumnIndex(MediaStore.Video.Media.DURATION).takeIf { it >= 0 }
                        ?.let { durationMs = cursor.getLong(it).takeIf { d -> d > 0 } }
                }
            }
        }

        PhotoDetails(
            displayName = photo.displayName,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            durationMs = durationMs,
            locationLabel = readLocationLabel(photo)
        )
    }

    private fun readLocationLabel(photo: Photo): String? {
        val latLong = runCatching {
            val originalUri = MediaStore.setRequireOriginal(photo.uri)
            context.contentResolver.openInputStream(originalUri)?.use { stream ->
                ExifInterface(stream).latLong
            }
        }.getOrNull() ?: return null

        return runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault())
                .getFromLocation(latLong[0], latLong[1], 1)
                ?.firstOrNull()
                ?.getAddressLine(0)
        }.getOrNull()
    }
}
