package com.green3077.photoorganizer.data

import android.content.Context
import android.location.Geocoder
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.green3077.photoorganizer.model.LocationGroup
import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.model.PhotoLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class LocationRepository(private val context: Context) {

    /**
     * ACCESS_MEDIA_LOCATION 권한이 있어야 원본 EXIF GPS 좌표를 읽을 수 있다.
     * 좌표가 있는 사진만 가까운 것끼리 묶어 장소 그룹으로 반환한다.
     */
    suspend fun loadLocationGroups(photos: List<Photo>): List<LocationGroup> = withContext(Dispatchers.IO) {
        val located = photos.mapNotNull { photo -> readLocation(photo)?.let { photo to it } }
        LocationClusterer.cluster(located)
            .map { cluster ->
                LocationGroup(
                    centerLat = cluster.lat,
                    centerLon = cluster.lon,
                    placeName = reverseGeocode(cluster.lat, cluster.lon),
                    photos = cluster.items.sortedByDescending { it.dateTaken }
                )
            }
            .sortedByDescending { it.photoCount }
    }

    private fun readLocation(photo: Photo): PhotoLocation? = try {
        val originalUri = MediaStore.setRequireOriginal(photo.uri)
        context.contentResolver.openInputStream(originalUri)?.use { stream ->
            ExifInterface(stream).latLong?.let { PhotoLocation(it[0], it[1]) }
        }
    } catch (e: Exception) {
        null
    }

    private fun reverseGeocode(lat: Double, lon: Double): String = try {
        @Suppress("DEPRECATION")
        val address = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
        address?.locality ?: address?.subAdminArea ?: address?.adminArea ?: coordinateLabel(lat, lon)
    } catch (e: Exception) {
        coordinateLabel(lat, lon)
    }

    private fun coordinateLabel(lat: Double, lon: Double) =
        String.format(Locale.getDefault(), "%.4f, %.4f", lat, lon)
}
