package com.commonsengine.identity.infrastructure.persistence

import com.commonsengine.identity.domain.WorkerProfile
import com.commonsengine.identity.domain.MemberId
import com.commonsengine.platform.domain.ServiceType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 劳动者档案 JPA 实体
 */
@Entity
@Table(name = "worker_profiles")
class WorkerProfileEntity(

    @Id
    @Column(name = "member_id", length = 36)
    val memberId: String,

    @Column(name = "service_types", nullable = false, length = 200)
    var serviceTypesCsv: String,

    @Column(name = "work_region", nullable = false, length = 100)
    var workRegion: String,

    @Column(name = "license_number", length = 100)
    var licenseNumber: String?,

    @Column(name = "rating", nullable = false)
    var rating: Double,

    @Column(name = "total_completed_orders", nullable = false)
    var totalCompletedOrders: Int,
)

fun WorkerProfileEntity.toDomain() = WorkerProfile(
    memberId = MemberId(memberId),
    serviceTypes = serviceTypesCsv
        .split(",")
        .filter { it.isNotBlank() }
        .map { ServiceType.valueOf(it.trim()) }
        .toSet(),
    workRegion = workRegion,
    licenseNumber = licenseNumber,
    rating = rating,
    totalCompletedOrders = totalCompletedOrders,
)

fun WorkerProfile.toEntity() = WorkerProfileEntity(
    memberId = memberId.value,
    serviceTypesCsv = serviceTypes.joinToString(",") { it.name },
    workRegion = workRegion,
    licenseNumber = licenseNumber,
    rating = rating,
    totalCompletedOrders = totalCompletedOrders,
)
