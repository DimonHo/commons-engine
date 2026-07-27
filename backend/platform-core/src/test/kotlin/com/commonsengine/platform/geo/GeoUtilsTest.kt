package com.commonsengine.platform.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeoUtilsTest {

    @Test
    fun `distance between same point is zero`() {
        val p = GeoPoint(39.9042, 116.4074)
        assertEquals(0.0, GeoUtils.distance(p, p), 0.001)
    }

    @Test
    fun `distance Beijing to Tianjin is approximately 120km`() {
        val beijing = GeoPoint(39.9042, 116.4074)
        val tianjin = GeoPoint(39.0842, 117.2009)
        val dist = GeoUtils.distance(beijing, tianjin)
        // 约 110-130km
        assertTrue(dist in 100_000.0..140_000.0, "北京到天津约 120km，实际 ${dist / 1000}km")
    }

    @Test
    fun `isWithinRadius works correctly`() {
        val center = GeoPoint(39.9042, 116.4074)
        val near = GeoPoint(39.9050, 116.4080)   // ~100m
        val far = GeoPoint(40.0000, 116.5000)    // ~12km

        assertTrue(GeoUtils.isWithinRadius(center, near, 500.0))
        assertFalse(GeoUtils.isWithinRadius(center, far, 500.0))
    }

    @Test
    fun `negative latitude is valid for southern hemisphere`() {
        val sydney = GeoPoint(-33.8688, 151.2093)
        val melbourne = GeoPoint(-37.8136, 144.9631)
        val dist = GeoUtils.distance(sydney, melbourne)
        assertTrue(dist in 700_000.0..900_000.0, "悉尼到墨尔本约 880km，实际 ${dist / 1000}km")
    }
}
