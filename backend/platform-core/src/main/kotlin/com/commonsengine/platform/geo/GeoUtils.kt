package com.commonsengine.platform.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 经纬度坐标
 */
data class GeoPoint(val lat: Double, val lng: Double) {
    init {
        require(lat in -90.0..90.0) { "纬度必须在 [-90, 90] 范围内，实际: $lat" }
        require(lng in -180.0..180.0) { "经度必须在 [-180, 180] 范围内，实际: $lng" }
    }
}

/**
 * 地理空间计算工具
 */
object GeoUtils {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /**
     * Haversine 公式计算两点间球面距离（米）
     */
    fun distance(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val sinDLat = sin(dLat / 2)
        val sinDLng = sin(dLng / 2)
        val h = sinDLat.pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sinDLng.pow(2)
        val c = 2 * atan2(sqrt(h), sqrt(1 - h))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * 判断点 b 是否在以 a 为中心、radiusMeters 为半径的圆内
     */
    fun isWithinRadius(center: GeoPoint, point: GeoPoint, radiusMeters: Double): Boolean =
        distance(center, point) <= radiusMeters
}
