package com.commonsengine.matching.service

import com.commonsengine.matching.infrastructure.persistence.WorkerLocationEntity
import com.commonsengine.matching.infrastructure.persistence.WorkerLocationRepository
import com.commonsengine.matching.infrastructure.persistence.toDomain
import com.commonsengine.platform.domain.Worker
import com.commonsengine.platform.geo.GeoPoint
import com.commonsengine.platform.geo.GeoUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.math.cos
import kotlin.math.PI

/**
 * 劳动者位置服务
 *
 * 职责：
 * 1. 接收劳动者心跳上报，持久化最新位置
 * 2. 空间检索：给定一个坐标点和半径，返回该范围内的活跃劳动者
 *
 * 检索策略：bounding box 预筛选（DB 层）→ Haversine 精确过滤（应用层）
 * 这个两级过滤保证：DB 查询通用（H2/PostgreSQL 兼容），结果精确。
 */
@Service
open class WorkerLocationService(
    private val repository: WorkerLocationRepository,
) {

    /**
     * 上报/更新劳动者位置（心跳）
     */
    @Transactional
    open fun upsertLocation(
        workerId: String,
        name: String,
        lat: Double,
        lng: Double,
        serviceTypes: String = "",
        rating: Double = 5.0,
        activeOrderCount: Int = 0,
    ) {
        val existing = repository.findById(workerId).orElse(null)
        if (existing != null) {
            existing.name = name
            existing.lat = lat
            existing.lng = lng
            existing.serviceTypesCsv = serviceTypes
            existing.rating = rating
            existing.activeOrderCount = activeOrderCount
            existing.lastSeenAt = Instant.now()
            repository.save(existing)
        } else {
            repository.save(
                WorkerLocationEntity(
                    workerId = workerId,
                    name = name,
                    lat = lat,
                    lng = lng,
                    serviceTypesCsv = serviceTypes,
                    rating = rating,
                    activeOrderCount = activeOrderCount,
                    lastSeenAt = Instant.now(),
                ),
            )
        }
    }

    /**
     * 查找坐标点周围 radiusMeters 范围内的活跃劳动者
     *
     * @param center 搜索中心点
     * @param radiusMeters 搜索半径（米）
     * @param maxActiveOrders 最大活跃订单数（排除满单）
     * @return 精确距离内的劳动者列表（已按距离排序）
     */
    @Transactional(readOnly = true)
    open fun findNearbyWorkers(
        center: GeoPoint,
        radiusMeters: Double,
        maxActiveOrders: Int = 3,
    ): List<Worker> {
        // 1. 计算 bounding box（圆形的外接矩形）
        val box = boundingBox(center, radiusMeters)

        // 2. DB 层预筛选
        val candidates = repository.findInBoundingBox(
            minLat = box.minLat,
            maxLat = box.maxLat,
            minLng = box.minLng,
            maxLng = box.maxLng,
            maxActiveOrders = maxActiveOrders,
        )

        // 3. 应用层 Haversine 精确过滤
        return candidates
            .map { it.toDomain() }
            .filter { worker ->
                GeoUtils.isWithinRadius(center, worker.currentLocation, radiusMeters)
            }
            .sortedBy { GeoUtils.distance(center, it.currentLocation) }
    }

    /**
     * 计算给定坐标和半径的 bounding box（外接矩形）
     *
     * 使用简化的度数转换——对中国纬度范围（约 20-50°）足够精确。
     * 1° 纬度 ≈ 111km，1° 经度 ≈ 111km × cos(lat)
     */
    private fun boundingBox(center: GeoPoint, radiusMeters: Double): BoundingBox {
        val latDelta = radiusMeters / 111_000.0
        val lngDelta = radiusMeters / (111_000.0 * cos(center.lat * PI / 180))

        return BoundingBox(
            minLat = (center.lat - latDelta).coerceIn(-90.0, 90.0),
            maxLat = (center.lat + latDelta).coerceIn(-90.0, 90.0),
            minLng = (center.lng - lngDelta).coerceIn(-180.0, 180.0),
            maxLng = (center.lng + lngDelta).coerceIn(-180.0, 180.0),
        )
    }

    private data class BoundingBox(
        val minLat: Double,
        val maxLat: Double,
        val minLng: Double,
        val maxLng: Double,
    )
}
