package com.green3077.photoorganizer.data

import com.green3077.photoorganizer.model.PhotoLocation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GPS 좌표를 가까운 것끼리 묶는 순수 로직. 안드로이드 프레임워크에 의존하지 않아 유닛 테스트가 쉽다.
 */
object LocationClusterer {
    const val DEFAULT_RADIUS_METERS = 500.0
    private const val EARTH_RADIUS_METERS = 6371000.0

    class Cluster<T>(lat: Double, lon: Double, firstItem: T) {
        var lat: Double = lat
            private set
        var lon: Double = lon
            private set
        private val mutableItems = mutableListOf(firstItem)
        val items: List<T> get() = mutableItems

        fun add(item: T, itemLat: Double, itemLon: Double) {
            val n = mutableItems.size
            lat = (lat * n + itemLat) / (n + 1)
            lon = (lon * n + itemLon) / (n + 1)
            mutableItems.add(item)
        }
    }

    /**
     * 각 항목을 가장 가까운 클러스터(중심까지 거리가 [radiusMeters] 이내)에 합치고,
     * 없으면 새 클러스터를 만드는 단순 그리디 클러스터링.
     */
    fun <T> cluster(
        located: List<Pair<T, PhotoLocation>>,
        radiusMeters: Double = DEFAULT_RADIUS_METERS
    ): List<Cluster<T>> {
        val clusters = mutableListOf<Cluster<T>>()
        for ((item, loc) in located) {
            val nearest = clusters.minByOrNull { distanceMeters(it.lat, it.lon, loc.latitude, loc.longitude) }
            if (nearest != null && distanceMeters(nearest.lat, nearest.lon, loc.latitude, loc.longitude) <= radiusMeters) {
                nearest.add(item, loc.latitude, loc.longitude)
            } else {
                clusters.add(Cluster(loc.latitude, loc.longitude, item))
            }
        }
        return clusters
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }
}
