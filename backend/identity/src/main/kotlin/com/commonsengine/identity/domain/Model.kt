package com.commonsengine.identity.domain

import com.commonsengine.platform.geo.GeoPoint
import java.time.Instant
import java.util.UUID

/**
 * 合作社成员角色
 */
enum class MemberRole {
    WORKER,      // 劳动者（骑手/司机/家政工等）
    CONSUMER,    // 消费者
    COMMUNITY,   // 社区代表
}

/**
 * 合作社成员——平台的真正主人
 *
 * 每个成员有角色（可以是多个）、投票权和劳动份额记录。
 * 这是公地引擎与资本平台的根本区别：成员不是"用户"，是所有者。
 */
data class Member(
    val id: MemberId,
    val name: String,
    val phone: String,             // 加密存储，仅用于服务运行
    val roles: Set<MemberRole>,
    val registeredAt: Instant = Instant.now(),
    val status: MemberStatus = MemberStatus.ACTIVE,
    val laborShares: Int = 0,      // 劳动份额（用于利润分配，非股权）
)

@JvmInline
value class MemberId(val value: String) {
    companion object {
        fun random() = MemberId(UUID.randomUUID().toString())
    }
}

enum class MemberStatus {
    ACTIVE,       // 活跃
    SUSPENDED,    // 暂停（违反规则/调查中）
    WITHDRAWN,    // 退社
}

/**
 * 劳动者档案——Member 的扩展信息
 */
data class WorkerProfile(
    val memberId: MemberId,
    val serviceTypes: Set<com.commonsengine.platform.domain.ServiceType>,
    val workRegion: String,        // 工作区域（城市/区）
    val licenseNumber: String? = null,  // 行业准入证件号（如网约车驾驶员证），加密存储
    val rating: Double = 5.0,
    val totalCompletedOrders: Int = 0,
)

/**
 * 身份验证状态
 */
enum class VerificationStatus {
    UNVERIFIED,    // 未验证
    PENDING,       // 验证中
    VERIFIED,      // 已验证
    REJECTED,      // 验证失败
}

/**
 * 身份验证记录
 */
data class IdentityVerification(
    val memberId: MemberId,
    val type: VerificationType,
    val status: VerificationStatus,
    val submittedAt: Instant = Instant.now(),
    val verifiedAt: Instant? = null,
    val notes: String? = null,
)

enum class VerificationType {
    PHONE,           // 手机号验证
    ID_CARD,         // 身份证验证
    LICENSE,         // 行业准入证件验证
    BACKGROUND_CHECK, // 背景调查
}
