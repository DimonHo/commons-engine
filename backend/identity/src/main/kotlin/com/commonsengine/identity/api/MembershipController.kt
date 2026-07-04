package com.commonsengine.identity.api

import com.commonsengine.identity.domain.MemberRole
import com.commonsengine.identity.domain.VerificationType
import com.commonsengine.identity.domain.WorkerProfile
import com.commonsengine.identity.service.MembershipService
import com.commonsengine.platform.domain.ServiceType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 会员管理 REST API
 */
@RestController
@RequestMapping("/api/v1/members")
open class MembershipController(private val service: MembershipService) {

    /** 注册新成员 */
    @PostMapping("/register")
    fun register(@RequestBody body: RegisterRequest): MemberResponse {
        val member = service.register(body.name, body.phone, body.roles.mapNotNull { runCatching { MemberRole.valueOf(it) }.getOrNull() }.toSet())
        return member.toResponse()
    }

    /** 查询成员详情 */
    @GetMapping("/{id}")
    fun findById(@PathVariable id: String): MemberResponse? = service.findById(com.commonsengine.identity.domain.MemberId(id))?.toResponse()

    /** 查询全部成员 */
    @GetMapping
    fun findAll(): List<MemberResponse> = service.findAll().map { it.toResponse() }

    /** 统计 */
    @GetMapping("/stats")
    fun stats(): Map<String, Int> = service.roleStatistics().mapKeys { it.key.name }

    /** 注册劳动者档案 */
    @PostMapping("/{id}/worker-profile")
    fun registerWorkerProfile(@PathVariable id: String, @RequestBody body: WorkerProfileRequest): WorkerProfileResponse? {
        val profile = WorkerProfile(
            memberId = com.commonsengine.identity.domain.MemberId(id),
            serviceTypes = body.serviceTypes.mapNotNull { runCatching { ServiceType.valueOf(it) }.getOrNull() }.toSet(),
            workRegion = body.workRegion,
            licenseNumber = body.licenseNumber,
        )
        val saved = service.registerWorkerProfile(profile)
        return WorkerProfileResponse(
            memberId = saved.memberId.value,
            serviceTypes = saved.serviceTypes.map { it.name },
            workRegion = saved.workRegion,
            rating = saved.rating,
            totalCompletedOrders = saved.totalCompletedOrders,
        )
    }
}

// ── DTO ──────────────────────────────────────────────────────────

data class RegisterRequest(
    val name: String,
    val phone: String,
    val roles: List<String>,
)

data class MemberResponse(
    val id: String,
    val name: String,
    val roles: List<String>,
    val status: String,
    val registeredAt: String,
    val laborShares: Int,
)

data class WorkerProfileRequest(
    val serviceTypes: List<String>,
    val workRegion: String,
    val licenseNumber: String? = null,
)

data class WorkerProfileResponse(
    val memberId: String,
    val serviceTypes: List<String>,
    val workRegion: String,
    val rating: Double,
    val totalCompletedOrders: Int,
)

private fun com.commonsengine.identity.domain.Member.toResponse() = MemberResponse(
    id = id.value,
    name = name,
    roles = roles.map { it.name },
    status = status.name,
    registeredAt = registeredAt.toString(),
    laborShares = laborShares,
)
