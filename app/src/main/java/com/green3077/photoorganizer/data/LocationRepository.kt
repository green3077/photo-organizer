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
import kotlin.math.roundToInt

/**
 * "장소별"(반경 500m 클러스터)이 아니라 "나라별"로 묶는다. 사진마다, 심지어 클러스터마다
 * 지오코딩을 부르면 여행 사진이 많을 때 너무 오래 걸려서, 좌표를 약 1도(~111km) 격자로
 * 뭉쳐 그 격자당 지오코딩을 딱 한 번만 호출한 뒤 나라 단위로 합친다. 기기 로케일의
 * 국가(=집)는 "해외 사진만" 보려는 목적에 맞게 제외한다.
 */
class LocationRepository(private val context: Context) {

    suspend fun loadLocationGroups(photos: List<Photo>): List<LocationGroup> = withContext(Dispatchers.IO) {
        val homeCountryCode = Locale.getDefault().country
        val located = photos.mapNotNull { photo -> readLocation(photo)?.let { photo to it } }
        val byGridCell = located.groupBy { (_, loc) -> gridCellOf(loc) }

        val photosByCountry = mutableMapOf<String, MutableList<Photo>>()
        val countryNames = mutableMapOf<String, String>()

        for ((cell, entries) in byGridCell) {
            val (code, name) = reverseGeocodeCountry(cell) ?: continue
            if (homeCountryCode.isNotBlank() && code.equals(homeCountryCode, ignoreCase = true)) continue
            photosByCountry.getOrPut(code) { mutableListOf() }.addAll(entries.map { it.first })
            countryNames[code] = name
        }

        photosByCountry.map { (code, list) ->
            LocationGroup(
                placeName = countryNames[code] ?: code,
                photos = list.sortedByDescending { it.dateTaken }
            )
        }.sortedByDescending { it.photoCount }
    }

    private fun gridCellOf(loc: PhotoLocation): Pair<Int, Int> =
        loc.latitude.roundToInt() to loc.longitude.roundToInt()

    private fun readLocation(photo: Photo): PhotoLocation? = try {
        val originalUri = MediaStore.setRequireOriginal(photo.uri)
        context.contentResolver.openInputStream(originalUri)?.use { stream ->
            ExifInterface(stream).latLong?.let { PhotoLocation(it[0], it[1]) }
        }
    } catch (e: Exception) {
        null
    }

    private fun reverseGeocodeCountry(cell: Pair<Int, Int>): Pair<String, String>? = try {
        @Suppress("DEPRECATION")
        val address = Geocoder(context, Locale.getDefault())
            .getFromLocation(cell.first.toDouble(), cell.second.toDouble(), 1)
            ?.firstOrNull()
        val code = address?.countryCode ?: return null
        code to (address.countryName ?: code)
    } catch (e: Exception) {
        null
    }
}
