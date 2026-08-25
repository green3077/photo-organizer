package com.green3077.photoorganizer.data

import com.green3077.photoorganizer.model.PhotoLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationClustererTest {

    @Test
    fun `nearby points are grouped into a single cluster`() {
        val cityHall = PhotoLocation(37.5663, 126.9779)
        val nearby = PhotoLocation(37.5664, 126.9780) // 약 10m 거리
        val busan = PhotoLocation(35.1796, 129.0756) // 약 325km 거리

        val located = listOf(
            "photo1" to cityHall,
            "photo2" to nearby,
            "photo3" to busan
        )

        val clusters = LocationClusterer.cluster(located, radiusMeters = 500.0)

        assertEquals(2, clusters.size)
        val seoulCluster = clusters.first { it.items.contains("photo1") }
        assertEquals(setOf("photo1", "photo2"), seoulCluster.items.toSet())
        val busanCluster = clusters.first { it.items.contains("photo3") }
        assertEquals(1, busanCluster.items.size)
    }

    @Test
    fun `points just outside the radius stay in separate clusters`() {
        val a = PhotoLocation(37.5663, 126.9779)
        val b = PhotoLocation(37.5700, 126.9779) // 위도 0.0037도 차이 ~ 410m

        val clusters = LocationClusterer.cluster(listOf("a" to a, "b" to b), radiusMeters = 100.0)

        assertEquals(2, clusters.size)
    }

    @Test
    fun `distanceMeters matches known great-circle distance within tolerance`() {
        // 서울시청 -> 부산시청 대략 325km
        val distance = LocationClusterer.distanceMeters(37.5663, 126.9779, 35.1796, 129.0756)
        assertTrue("expected around 325km but was $distance", distance in 300_000.0..350_000.0)
    }
}
