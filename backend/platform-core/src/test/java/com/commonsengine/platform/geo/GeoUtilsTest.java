package com.commonsengine.platform.geo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GeoUtils unit tests.
 *
 * <p>Converted from the Kotlin original. Validates Haversine distance and
 * radius containment for both northern and southern hemispheres.
 */
class GeoUtilsTest {

    @Test
    void distanceBetweenSamePointIsZero() {
        GeoPoint p = new GeoPoint(39.9042, 116.4074);
        assertEquals(0.0, GeoUtils.distance(p, p), 0.001);
    }

    @Test
    void distanceBeijingToTianjinIsApproximately120km() {
        GeoPoint beijing = new GeoPoint(39.9042, 116.4074);
        GeoPoint tianjin = new GeoPoint(39.0842, 117.2009);
        double dist = GeoUtils.distance(beijing, tianjin);
        // 约 110-130km
        assertTrue(dist >= 100_000.0 && dist <= 140_000.0,
                "北京到天津约 120km，实际 " + (dist / 1000) + "km");
    }

    @Test
    void isWithinRadiusWorksCorrectly() {
        GeoPoint center = new GeoPoint(39.9042, 116.4074);
        GeoPoint near = new GeoPoint(39.9050, 116.4080);   // ~100m
        GeoPoint far = new GeoPoint(40.0000, 116.5000);    // ~12km

        assertTrue(GeoUtils.isWithinRadius(center, near, 500.0));
        assertFalse(GeoUtils.isWithinRadius(center, far, 500.0));
    }

    @Test
    void negativeLatitudeIsValidForSouthernHemisphere() {
        GeoPoint sydney = new GeoPoint(-33.8688, 151.2093);
        GeoPoint melbourne = new GeoPoint(-37.8136, 144.9631);
        double dist = GeoUtils.distance(sydney, melbourne);
        assertTrue(dist >= 700_000.0 && dist <= 900_000.0,
                "悉尼到墨尔本约 880km，实际 " + (dist / 1000) + "km");
    }
}
