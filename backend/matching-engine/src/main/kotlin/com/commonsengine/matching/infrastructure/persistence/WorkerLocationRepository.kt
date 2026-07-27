package com.commonsengine.matching.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * 劳动者位置 Repository
 *
 * 空间检索使用 bounding box 预筛选（DB 无关，H2 兼容）。
 * 生产环境 PostgreSQL+PostGIS 可替换为 ST_DWithin 原生查询获得更高性能。
 */
@Repository
interface WorkerLocationRepository : JpaRepository<WorkerLocationEntity, String> {

    /**
     * Bounding box 查询：返回矩形区域内的活跃劳动者。
     *
     * 矩形由 (minLat, maxLat, minLng, maxLng) 定义，
     * 是圆形搜索半径的外接矩形——快速排除绝大部分不相关记录。
     *
     * 排除满单劳动者（active_order_count < maxActiveOrders）。
     */
    @Query("""
        SELECT w FROM WorkerLocationEntity w
        WHERE w.lat BETWEEN :minLat AND :maxLat
          AND w.lng BETWEEN :minLng AND :maxLng
          AND w.activeOrderCount < :maxActiveOrders
        ORDER BY w.rating DESC
    """)
    fun findInBoundingBox(
        @Param("minLat") minLat: Double,
        @Param("maxLat") maxLat: Double,
        @Param("minLng") minLng: Double,
        @Param("maxLng") maxLng: Double,
        @Param("maxActiveOrders") maxActiveOrders: Int,
    ): List<WorkerLocationEntity>

    /**
     * 更新劳动者位置（心跳上报）
     */
    @Modifying
    @Query("""
        UPDATE WorkerLocationEntity w
        SET w.lat = :lat, w.lng = :lng, w.lastSeenAt = :timestamp
        WHERE w.workerId = :workerId
    """)
    fun updateLocation(
        @Param("workerId") workerId: String,
        @Param("lat") lat: Double,
        @Param("lng") lng: Double,
        @Param("timestamp") timestamp: java.time.Instant,
    ): Int
}
