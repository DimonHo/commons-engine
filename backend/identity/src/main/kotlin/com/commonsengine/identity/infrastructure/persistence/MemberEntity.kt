package com.commonsengine.identity.infrastructure.persistence

import com.commonsengine.identity.domain.MemberId
import com.commonsengine.identity.domain.MemberRole
import com.commonsengine.identity.domain.MemberStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 会员 JPA 实体——对应 members 表
 *
 * 与领域模型 Member 分离，保持领域纯净。
 * Repository 层负责 entity ↔ domain 的映射。
 */
@Entity
@Table(name = "members")
class MemberEntity(

    @Id
    @Column(name = "id", length = 36)
    val id: String,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Column(name = "phone", nullable = false, length = 20)
    var phone: String,

    @Column(name = "roles", nullable = false, length = 200)
    var rolesCsv: String,

    @Column(name = "registered_at", nullable = false)
    val registeredAt: Instant,

    @Column(name = "status", nullable = false, length = 20)
    var status: String,

    @Column(name = "labor_shares", nullable = false)
    var laborShares: Int,
) {
    fun roles(): Set<MemberRole> = rolesCsv
        .split(",")
        .filter { it.isNotBlank() }
        .map { MemberRole.valueOf(it.trim()) }
        .toSet()

    fun memberStatus(): MemberStatus = MemberStatus.valueOf(status)
}

/**
 * Entity → Domain 映射
 */
fun MemberEntity.toDomain() = com.commonsengine.identity.domain.Member(
    id = MemberId(id),
    name = name,
    phone = phone,
    roles = roles(),
    registeredAt = registeredAt,
    status = memberStatus(),
    laborShares = laborShares,
)

/**
 * Domain → Entity 映射（新建）
 */
fun com.commonsengine.identity.domain.Member.toEntity() = MemberEntity(
    id = id.value,
    name = name,
    phone = phone,
    rolesCsv = roles.joinToString(",") { it.name },
    registeredAt = registeredAt,
    status = status.name,
    laborShares = laborShares,
)
