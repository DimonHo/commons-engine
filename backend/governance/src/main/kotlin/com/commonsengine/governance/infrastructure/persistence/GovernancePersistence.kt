package com.commonsengine.governance.infrastructure.persistence

import com.commonsengine.governance.domain.Proposal
import com.commonsengine.governance.domain.ProposalId
import com.commonsengine.governance.domain.ProposalStatus
import com.commonsengine.governance.domain.ProposalType
import com.commonsengine.governance.domain.StakeholderType
import com.commonsengine.governance.domain.Vote
import com.commonsengine.governance.domain.VoteChoice
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

// ══════════════════════════════════════════════════════
// ProposalEntity
// ══════════════════════════════════════════════════════

/**
 * 提案 JPA 实体
 */
@Entity
@Table(name = "proposals")
class ProposalEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "proposal_id", nullable = false, length = 36)
    val proposalId: String,

    @Column(name = "title", nullable = false, length = 500)
    val title: String,

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    val description: String,

    @Column(name = "proposed_by", nullable = false, length = 36)
    val proposedBy: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    val type: ProposalType,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ProposalStatus,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "discussion_deadline", nullable = false)
    var discussionDeadline: Instant,
)

// ── Proposal Entity ↔ Domain ──────────────────────────

fun ProposalEntity.toDomain(): Proposal = Proposal(
    id = ProposalId(proposalId),
    title = title,
    description = description,
    proposedBy = proposedBy,
    type = type,
    createdAt = createdAt,
    discussionDeadline = discussionDeadline,
    status = status,
)

fun Proposal.toEntity(): ProposalEntity = ProposalEntity(
    proposalId = id.value,
    title = title,
    description = description,
    proposedBy = proposedBy,
    type = type,
    status = status,
    createdAt = createdAt,
    discussionDeadline = discussionDeadline,
)

// ══════════════════════════════════════════════════════
// VoteEntity
// ══════════════════════════════════════════════════════

/**
 * 投票 JPA 实体
 */
@Entity
@Table(name = "votes")
class VoteEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "proposal_id", nullable = false, length = 36)
    val proposalId: String,

    @Column(name = "voter_id", nullable = false, length = 36)
    val voterId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "stakeholder_type", nullable = false, length = 20)
    val stakeholderType: StakeholderType,

    @Enumerated(EnumType.STRING)
    @Column(name = "choice", nullable = false, length = 10)
    val choice: VoteChoice,

    @Column(name = "voted_at", nullable = false)
    val votedAt: Instant,
)

// ── Vote Entity ↔ Domain ──────────────────────────────

fun VoteEntity.toDomain(): Vote = Vote(
    proposalId = com.commonsengine.governance.domain.ProposalId(proposalId),
    voterId = voterId,
    stakeholderType = stakeholderType,
    choice = choice,
    votedAt = votedAt,
)

fun Vote.toEntity(): VoteEntity = VoteEntity(
    proposalId = proposalId.value,
    voterId = voterId,
    stakeholderType = stakeholderType,
    choice = choice,
    votedAt = votedAt,
)
