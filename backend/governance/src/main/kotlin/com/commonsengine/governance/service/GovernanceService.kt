package com.commonsengine.governance.service

import com.commonsengine.governance.domain.Proposal
import com.commonsengine.governance.domain.ProposalId
import com.commonsengine.governance.domain.ProposalStatus
import com.commonsengine.governance.domain.ProposalType
import com.commonsengine.governance.domain.StakeholderType
import com.commonsengine.governance.domain.Vote
import com.commonsengine.governance.domain.VoteChoice
import com.commonsengine.governance.domain.VoteResult
import com.commonsengine.governance.domain.weight
import com.commonsengine.governance.infrastructure.persistence.ProposalRepository
import com.commonsengine.governance.infrastructure.persistence.VoteRepository
import com.commonsengine.governance.infrastructure.persistence.toDomain
import com.commonsengine.governance.infrastructure.persistence.toEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 治理服务——合作社民主治理的技术载体
 *
 * 核心原则：
 * 1. 多利益相关方加权投票（劳动者 40% / 消费者 30% / 社区 30%）
 * 2. 提案至少讨论 30 天后才能投票
 * 3. 章程修改需 2/3 多数 + 45 天讨论
 * 4. 涉及不可篡改底线的提案无效
 *
 * 注意：治理参数的最终值需由全体大会决定，本服务只做技术载体。
 *
 * 持久化：使用 JPA + PostgreSQL，重启不丢数据。
 */
@Service
open class GovernanceService(
    private val proposalRepository: ProposalRepository,
    private val voteRepository: VoteRepository,
) {

    /** 提交提案 */
    @Transactional
    open fun createProposal(
        title: String,
        description: String,
        proposedBy: String,
        type: ProposalType = ProposalType.OTHER,
    ): Proposal {
        require(title.isNotBlank()) { "提案标题不能为空" }

        val discussionDays = if (type == ProposalType.CHARTER_AMENDMENT) 45 else 30

        val proposal = Proposal(
            id = ProposalId.random(),
            title = title,
            description = description,
            proposedBy = proposedBy,
            type = type,
            discussionDeadline = Instant.now().plus(discussionDays.toLong(), ChronoUnit.DAYS),
        )
        proposalRepository.save(proposal.toEntity())
        return proposal
    }

    /** 开启投票（讨论期结束后） */
    @Transactional
    open fun startVote(proposalId: ProposalId): Proposal? {
        val entity = proposalRepository.findByProposalId(proposalId.value)
            ?: return null
        val proposal = entity.toDomain()

        require(proposal.status == ProposalStatus.DISCUSSION) { "提案必须处于讨论状态" }
        require(Instant.now().isAfter(proposal.discussionDeadline)) {
            "讨论期未满，不能开始投票（截止: ${proposal.discussionDeadline}）"
        }

        entity.status = ProposalStatus.VOTING
        proposalRepository.save(entity)
        return entity.toDomain()
    }

    /** 投票 */
    @Transactional
    open fun castVote(
        proposalId: ProposalId,
        voterId: String,
        stakeholderType: StakeholderType,
        choice: VoteChoice,
    ): Vote {
        val entity = proposalRepository.findByProposalId(proposalId.value)
            ?: throw IllegalArgumentException("提案不存在: $proposalId")
        val proposal = entity.toDomain()

        require(proposal.status == ProposalStatus.VOTING) { "提案不在投票阶段" }

        // 检查是否已投票（一人一票）——数据库唯一约束保障
        require(!voteRepository.existsByProposalIdAndVoterId(proposalId.value, voterId)) {
            "$voterId 已对此提案投过票"
        }

        val vote = Vote(
            proposalId = proposalId,
            voterId = voterId,
            stakeholderType = stakeholderType,
            choice = choice,
        )
        voteRepository.save(vote.toEntity())
        return vote
    }

    /** 统计投票结果 */
    @Transactional
    open fun tallyVotes(proposalId: ProposalId): VoteResult {
        val proposalVotes = voteRepository.findByProposalId(proposalId.value).map { it.toDomain() }
        val entity = proposalRepository.findByProposalId(proposalId.value)
        val proposal = entity?.toDomain()

        val yesWeight = proposalVotes.filter { it.choice == VoteChoice.YES }
            .sumOf { it.stakeholderType.weight }
        val noWeight = proposalVotes.filter { it.choice == VoteChoice.NO }
            .sumOf { it.stakeholderType.weight }
        val abstainCount = proposalVotes.count { it.choice == VoteChoice.ABSTAIN }

        // 通过门槛：普通提案简单多数，章程修改需 2/3
        val threshold = if (proposal?.type == ProposalType.CHARTER_AMENDMENT) 2.0 / 3.0 else 0.5
        val totalValidWeight = yesWeight + noWeight
        val passed = if (totalValidWeight > 0) yesWeight / totalValidWeight > threshold else false

        // 更新提案状态
        if (entity != null && entity.status == ProposalStatus.VOTING) {
            entity.status = if (passed) ProposalStatus.APPROVED else ProposalStatus.REJECTED
            proposalRepository.save(entity)
        }

        return VoteResult(
            proposalId = proposalId,
            yesWeighted = yesWeight,
            noWeighted = noWeight,
            abstainCount = abstainCount,
            totalVotes = proposalVotes.size,
            passed = passed,
            breakdown = buildString {
                append("投票结果：赞成 ${"%.1f%%".format(yesWeight * 100)}（加权）/ ")
                append("反对 ${"%.1f%%".format(noWeight * 100)}（加权）/ ")
                append("弃权 $abstainCount 票。")
                append("通过门槛：${"%.0f%%".format(threshold * 100)}。")
                append(if (passed) "提案通过。" else "提案未通过。")
            },
        )
    }

    /** 查询所有提案 */
    @Transactional(readOnly = true)
    open fun findAllProposals(): List<Proposal> =
        proposalRepository.findAll().map { it.toDomain() }

    /** 查询提案详情 */
    @Transactional(readOnly = true)
    open fun findProposal(id: ProposalId): Proposal? =
        proposalRepository.findByProposalId(id.value)?.toDomain()
}
