package com.commonsengine.platform.geo;

/**
 * Geographic utility functions.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoUtils() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    /**
     * Great-circle distance between two coordinates using the Haversine formula.
     */
    public static double distance(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());

        double sinDLat = Math.sin(dLat / 2);
        double sinDLng = Math.sin(dLng / 2);
        double h = sinDLat * sinDLat
                + Math.cos(lat1) * Math.cos(lat2) * sinDLng * sinDLng;
        double c = 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));

        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Returns true when {@code point} is within {@code radiusMeters} of {@code center}.
     */
    public static boolean isWithinRadius(GeoPoint point, GeoPoint center, double radiusMeters) {
        return distance(point, center) <= radiusMeters;
    }
}
