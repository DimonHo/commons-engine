package com.commonsengine.platform.geo;

/**
 * Immutable geographic coordinate (latitude / longitude).
 */
public record GeoPoint(double lat, double lng) {

    public GeoPoint {
        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException(
                    "Latitude must be between -90 and 90, got: " + lat);
        }
        if (lng < -180.0 || lng > 180.0) {
            throw new IllegalArgumentException(
                    "Longitude must be between -180 and 180, got: " + lng);
        }
    }
}
