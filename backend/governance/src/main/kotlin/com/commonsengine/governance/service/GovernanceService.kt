package com.commonsengine.governance.service

import com.commonsengine.governance.domain.Proposal
import com.commonsengine.governance.domain.ProposalId
import com.commonsengine.governance.domain.ProposalStatus
import com.commonsengine.governance.domain.ProposalType
import com.commonsengine.governance.domain.StakeholderType
import com.commonsengine.governance.domain.StakeholderType.weight
import com.commonsengine.governance.domain.Vote
import com.commonsengine.governance.domain.VoteChoice
import com.commonsengine.governance.domain.VoteResult
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

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
 */
@Service
open class GovernanceService {

    private val proposals = ConcurrentHashMap<String, Proposal>()
    private val votes = CopyOnWriteArrayList<Vote>()

    /** 提交提案 */
    fun createProposal(
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
        proposals[proposal.id.value] = proposal
        return proposal
    }

    /** 开启投票（讨论期结束后） */
    fun startVote(proposalId: ProposalId): Proposal? {
        val proposal = proposals[proposalId.value] ?: return null
        require(proposal.status == ProposalStatus.DISCUSSION) { "提案必须处于讨论状态" }
        require(Instant.now().isAfter(proposal.discussionDeadline)) {
            "讨论期未满，不能开始投票（截止: ${proposal.discussionDeadline}）"
        }

        val updated = proposal.copy(status = ProposalStatus.VOTING)
        proposals[proposalId.value] = updated
        return updated
    }

    /** 投票 */
    fun castVote(
        proposalId: ProposalId,
        voterId: String,
        stakeholderType: StakeholderType,
        choice: VoteChoice,
    ): Vote {
        val proposal = proposals[proposalId.value]
            ?: throw IllegalArgumentException("提案不存在: $proposalId")
        require(proposal.status == ProposalStatus.VOTING) { "提案不在投票阶段" }

        // 检查是否已投票（一人一票）
        val alreadyVoted = votes.any { it.proposalId == proposalId && it.voterId == voterId }
        require(!alreadyVoted) { "$voterId 已对此提案投过票" }

        val vote = Vote(
            proposalId = proposalId,
            voterId = voterId,
            stakeholderType = stakeholderType,
            choice = choice,
        )
        votes.add(vote)
        return vote
    }

    /** 统计投票结果 */
    fun tallyVotes(proposalId: ProposalId): VoteResult {
        val proposalVotes = votes.filter { it.proposalId == proposalId }
        val proposal = proposals[proposalId.value]

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
        if (proposal != null && proposal.status == ProposalStatus.VOTING) {
            val newStatus = if (passed) ProposalStatus.APPROVED else ProposalStatus.REJECTED
            proposals[proposalId.value] = proposal.copy(status = newStatus)
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
    fun findAllProposals(): List<Proposal> = proposals.values.toList()

    /** 查询提案详情 */
    fun findProposal(id: ProposalId): Proposal? = proposals[id.value]
}
