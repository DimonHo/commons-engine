package com.commonsengine.dispute.infrastructure.persistence

import com.commonsengine.dispute.domain.Dispute
import com.commonsengine.dispute.domain.DisputeId
import com.commonsengine.dispute.domain.DisputePriority
import com.commonsengine.dispute.domain.DisputeStatus
import com.commonsengine.dispute.domain.DisputeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 纠纷工单 JPA 实体
 *
 * evidenceUrls: List<String> 以分号分隔存储。
 */
@Entity
@Table(name = "disputes")
class DisputeEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "dispute_id", nullable = false, length = 36)
    val disputeId: String,

    @Column(name = "transaction_id", nullable = false, length = 36)
    val transactionId: String,

    @Column(name = "filed_by", nullable = false, length = 36)
    val filedBy: String,

    @Column(name = "filed_against", nullable = false, length = 36)
    val filedAgainst: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    val type: DisputeType,

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 15)
    val priority: DisputePriority,

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    val description: String,

    /** 证据 URL 以分号分隔 */
    @Column(name = "evidence_urls", columnDefinition = "TEXT")
    val evidenceUrls: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: DisputeStatus,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,

    @Column(name = "resolution", columnDefinition = "TEXT")
    var resolution: String? = null,

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,
)

// ── Entity ↔ Domain ───────────────────────────────────

fun DisputeEntity.toDomain(): Dispute = Dispute(
    id = DisputeId(disputeId),
    transactionId = transactionId,
    filedBy = filedBy,
    filedAgainst = filedAgainst,
    type = type,
    priority = priority,
    description = description,
    evidenceUrls = evidenceUrls?.split(";")?.filter { it.isNotBlank() } ?: emptyList(),
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
    resolution = resolution,
    resolvedAt = resolvedAt,
)

fun Dispute.toEntity(): DisputeEntity = DisputeEntity(
    disputeId = id.value,
    transactionId = transactionId,
    filedBy = filedBy,
    filedAgainst = filedAgainst,
    type = type,
    priority = priority,
    description = description,
    evidenceUrls = if (evidenceUrls.isEmpty()) null else evidenceUrls.joinToString(";"),
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
    resolution = resolution,
    resolvedAt = resolvedAt,
)
