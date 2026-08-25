package com.green3077.photoorganizer.model

data class PhotoLocation(val latitude: Double, val longitude: Double)

data class LocationGroup(
    val centerLat: Double,
    val centerLon: Double,
    val placeName: String,
    val photos: List<Photo>
) {
    val photoCount: Int get() = photos.size
    val coverPhoto: Photo get() = photos.first()
}
