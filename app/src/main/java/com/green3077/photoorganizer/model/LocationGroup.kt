package com.green3077.photoorganizer.model

data class PhotoLocation(val latitude: Double, val longitude: Double)

/** 이름은 LocationGroup이지만 실제로는 "나라" 하나를 나타낸다(placeName = 나라 이름). */
data class LocationGroup(
    val placeName: String,
    val photos: List<Photo>
) {
    val photoCount: Int get() = photos.size
    val coverPhoto: Photo get() = photos.first()
}
