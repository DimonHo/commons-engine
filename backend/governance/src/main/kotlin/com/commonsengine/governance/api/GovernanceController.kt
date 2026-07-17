package com.commonsengine.governance.api

import com.commonsengine.governance.domain.ProposalId
import com.commonsengine.governance.domain.ProposalType
import com.commonsengine.governance.domain.StakeholderType
import com.commonsengine.governance.domain.VoteChoice
import com.commonsengine.governance.service.GovernanceService
import com.commonsengine.platform.support.Enums
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 治理 REST API——合作社民主治理的技术载体
 *
 * 核心原则：
 * 1. 多利益相关方加权投票（劳动者 40% / 消费者 30% / 社区 30%）
 * 2. 提案至少讨论 30 天后才能投票
 * 3. 章程修改需 2/3 多数 + 45 天讨论
 */
@RestController
@RequestMapping("/api/v1/governance")
open class GovernanceController(
    private val service: GovernanceService,
) {

    /** 提交提案 */
    @PostMapping("/proposals")
    fun createProposal(@Valid @RequestBody body: CreateProposalRequest): ProposalResponse {
        val proposal = service.createProposal(
            title = body.title,
            description = body.description,
            proposedBy = body.proposedBy,
            type = Enums.parse<ProposalType>(body.type),
        )
        return proposal.toResponse()
    }

    /** 查询所有提案 */
    @GetMapping("/proposals")
    fun findAllProposals(): List<ProposalResponse> =
        service.findAllProposals().map { it.toResponse() }

    /** 查询提案详情 */
    @GetMapping("/proposals/{proposalId}")
    fun findProposal(@PathVariable proposalId: String): ProposalResponse? =
        service.findProposal(ProposalId(proposalId))?.toResponse()

    /** 开启投票（讨论期结束后） */
    @PostMapping("/proposals/{proposalId}/start-vote")
    fun startVote(@PathVariable proposalId: String): ProposalResponse? {
        val proposal = service.startVote(ProposalId(proposalId))
        return proposal?.toResponse()
    }

    /** 投票 */
    @PostMapping("/proposals/{proposalId}/vote")
    fun castVote(
        @PathVariable proposalId: String,
        @Valid @RequestBody body: CastVoteRequest,
    ): VoteResponse {
        val vote = service.castVote(
            proposalId = ProposalId(proposalId),
            voterId = body.voterId,
            stakeholderType = Enums.parse<StakeholderType>(body.stakeholderType),
            choice = Enums.parse<VoteChoice>(body.choice),
        )
        return VoteResponse(
            proposalId = vote.proposalId.value,
            voterId = vote.voterId,
            stakeholderType = vote.stakeholderType.name,
            choice = vote.choice.name,
            votedAt = vote.votedAt.toString(),
        )
    }

    /** 统计投票结果 */
    @PostMapping("/proposals/{proposalId}/tally")
    fun tallyVotes(@PathVariable proposalId: String): VoteResultResponse {
        val result = service.tallyVotes(ProposalId(proposalId))
        return VoteResultResponse(
            proposalId = result.proposalId.value,
            yesWeighted = result.yesWeighted,
            noWeighted = result.noWeighted,
            abstainCount = result.abstainCount,
            totalVotes = result.totalVotes,
            passed = result.passed,
            breakdown = result.breakdown,
        )
    }
}

// ── DTO ──────────────────────────────────────────────────────────

data class CreateProposalRequest(
    @field:NotBlank val title: String,
    @field:NotBlank val description: String,
    @field:NotBlank val proposedBy: String,
    @field:NotBlank val type: String = "OTHER",  // POLICY_CHANGE / SETTLEMENT_RULE / BUDGET_ALLOCATION / CHARTER_AMENDMENT / OTHER
)

data class ProposalResponse(
    val id: String,
    val title: String,
    val description: String,
    val proposedBy: String,
    val type: String,
    val createdAt: String,
    val discussionDeadline: String,
    val status: String,
)

data class CastVoteRequest(
    @field:NotBlank val voterId: String,
    @field:NotBlank val stakeholderType: String,  // WORKER / CONSUMER / COMMUNITY
    @field:NotBlank val choice: String,           // YES / NO / ABSTAIN
)

data class VoteResponse(
    val proposalId: String,
    val voterId: String,
    val stakeholderType: String,
    val choice: String,
    val votedAt: String,
)

data class VoteResultResponse(
    val proposalId: String,
    val yesWeighted: Double,
    val noWeighted: Double,
    val abstainCount: Int,
    val totalVotes: Int,
    val passed: Boolean,
    val breakdown: String,
)

private fun com.commonsengine.governance.domain.Proposal.toResponse() = ProposalResponse(
    id = id.value,
    title = title,
    description = description,
    proposedBy = proposedBy,
    type = type.name,
    createdAt = createdAt.toString(),
    discussionDeadline = discussionDeadline.toString(),
    status = status.name,
)
