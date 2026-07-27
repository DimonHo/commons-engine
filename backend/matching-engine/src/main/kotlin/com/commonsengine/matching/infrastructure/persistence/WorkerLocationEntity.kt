package com.commonsengine.matching.infrastructure.persistence

import com.commonsengine.platform.domain.ServiceType
import com.commonsengine.platform.domain.Worker
import com.commonsengine.platform.domain.WorkerId
import com.commonsengine.platform.geo.GeoPoint
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 劳动者实时位置 JPA 实体
 *
 * 存储劳动者的最新位置、服务类型、评分和活跃订单数。
 * 用于匹配引擎的空间检索——bounding box 预筛选 + Haversine 精确过滤。
 *
 * 生产环境可在 lat/lng 上叠加 PostGIS geometry 列并用 ST_DWithin 优化，
 * 当前设计保持 DB 无关（H2 兼容），确保 CI 可测。
 */
@Entity
@Table(name = "worker_locations")
class WorkerLocationEntity(

    @Id
    @Column(name = "worker_id", length = 36)
    val workerId: String,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Column(name = "lat", nullable = false)
    var lat: Double,

    @Column(name = "lng", nullable = false)
    var lng: Double,

    @Column(name = "service_types", nullable = false, length = 200)
    var serviceTypesCsv: String,

    @Column(name = "rating", nullable = false)
    var rating: Double,

    @Column(name = "active_order_count", nullable = false)
    var activeOrderCount: Int,

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant,
)

/** Entity → 领域 Worker 映射 */
fun WorkerLocationEntity.toDomain(): Worker = Worker(
    id = WorkerId(workerId),
    name = name,
    currentLocation = GeoPoint(lat, lng),
    rating = rating,
    activeOrderCount = activeOrderCount,
    preferredServiceTypes = serviceTypesCsv
        .split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { runCatching { ServiceType.valueOf(it.trim()) }.getOrNull() }
        .toSet(),
)
