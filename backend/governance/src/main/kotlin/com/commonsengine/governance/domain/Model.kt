package com.commonsengine.governance.domain

import java.time.Instant
import java.util.UUID

/**
 * 利益相关方类型——多利益相关方合作社的核心
 *
 * 投票权重：劳动者 40% / 消费者 30% / 社区 30%（章程第 4.1 条）
 */
enum class StakeholderType {
    WORKER,      // 劳动者 40%
    CONSUMER,    // 消费者 30%
    COMMUNITY,   // 社区代表 30%
}

/**
 * 投票权重（章程第 4.1 条）
 */
val StakeholderType.weight: Double
    get() = when (this) {
        StakeholderType.WORKER -> 0.40
        StakeholderType.CONSUMER -> 0.30
        StakeholderType.COMMUNITY -> 0.30
    }

/**
 * 提案状态
 */
enum class ProposalStatus {
    DISCUSSION,    // 讨论中（至少 30 天）
    VOTING,        // 投票中
    APPROVED,      // 已通过
    REJECTED,      // 已否决
    WITHDRAWN,     // 已撤回
}

/**
 * 提案
 */
data class Proposal(
    val id: ProposalId,
    val title: String,
    val description: String,
    val proposedBy: String,
    val type: ProposalType,
    val createdAt: Instant = Instant.now(),
    val discussionDeadline: Instant,   // 讨论截止（至少 30 天后）
    val status: ProposalStatus = ProposalStatus.DISCUSSION,
)

@JvmInline
value class ProposalId(val value: String) {
    companion object { fun random() = ProposalId(UUID.randomUUID().toString()) }
}

enum class ProposalType {
    POLICY_CHANGE,        // 规则变更
    SETTLEMENT_RULE,      // 分账规则调整
    BUDGET_ALLOCATION,    // 预算分配
    CHARTER_AMENDMENT,    // 章程修改（需 2/3 多数 + 45 天讨论）
    OTHER,
}

/**
 * 投票
 */
data class Vote(
    val proposalId: ProposalId,
    val voterId: String,
    val stakeholderType: StakeholderType,
    val choice: VoteChoice,
    val votedAt: Instant = Instant.now(),
)

enum class VoteChoice {
    YES,
    NO,
    ABSTAIN,
}

/**
 * 投票结果
 */
data class VoteResult(
    val proposalId: ProposalId,
    val yesWeighted: Double,
    val noWeighted: Double,
    val abstainCount: Int,
    val totalVotes: Int,
    val passed: Boolean,
    val breakdown: String,
)
