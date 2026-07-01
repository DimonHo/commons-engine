package com.commonsengine.governance.service

import com.commonsengine.governance.domain.ProposalType
import com.commonsengine.governance.domain.StakeholderType
import com.commonsengine.governance.domain.VoteChoice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

class GovernanceServiceTest {

    private val service = GovernanceService()

    @Test
    fun `create proposal sets 30 day discussion for regular proposals`() {
        val proposal = service.createProposal("调整分账比例", "描述", "member-1", ProposalType.SETTLEMENT_RULE)
        val daysUntilDeadline = ChronoUnit.DAYS.between(Instant.now(), proposal.discussionDeadline)
        assertTrue(daysUntilDeadline in 29..30, "讨论期应约 30 天，实际: $daysUntilDeadline")
    }

    @Test
    fun `create proposal sets 45 day discussion for charter amendments`() {
        val proposal = service.createProposal("修改章程", "描述", "member-1", ProposalType.CHARTER_AMENDMENT)
        val daysUntilDeadline = ChronoUnit.DAYS.between(Instant.now(), proposal.discussionDeadline)
        assertTrue(daysUntilDeadline in 44..45, "章程修改讨论期应约 45 天，实际: $daysUntilDeadline")
    }

    @Test
    fun `cannot start vote before discussion deadline`() {
        val proposal = service.createProposal("测试", "描述", "member-1")
        assertThrows<IllegalArgumentException> { service.startVote(proposal.id) }
    }

    @Test
    fun `weighted voting works correctly`() {
        val proposal = service.createProposal("测试投票", "描述", "member-1")

        // 手动创建一个已过讨论期的提案
        val pastDeadline = Instant.now().minus(1, ChronoUnit.DAYS)
        val govField = GovernanceService::class.java.getDeclaredField("proposals")
        govField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val proposalsMap = govField.get(service) as ConcurrentHashMap<String, com.commonsengine.governance.domain.Proposal>
        proposalsMap[proposal.id.value] = proposal.copy(discussionDeadline = pastDeadline, status = com.commonsengine.governance.domain.ProposalStatus.DISCUSSION)

        service.startVote(proposal.id)

        // 投票：2个劳动者赞成（0.8），1个消费者反对（0.3）
        service.castVote(proposal.id, "w1", StakeholderType.WORKER, VoteChoice.YES)
        service.castVote(proposal.id, "w2", StakeholderType.WORKER, VoteChoice.YES)
        service.castVote(proposal.id, "c1", StakeholderType.CONSUMER, VoteChoice.NO)

        val result = service.tallyVotes(proposal.id)

        assertTrue(result.passed, "赞成 0.8 > 反对 0.3，应通过")
        assertEquals(0.8, result.yesWeighted, 0.001)
        assertEquals(0.3, result.noWeighted, 0.001)
    }

    @Test
    fun `one person one vote`() {
        val proposal = service.createProposal("测试", "描述", "member-1")
        // 直接设置提案为投票阶段
        val govField = GovernanceService::class.java.getDeclaredField("proposals")
        govField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val proposalsMap = govField.get(service) as ConcurrentHashMap<String, com.commonsengine.governance.domain.Proposal>
        proposalsMap[proposal.id.value] = proposal.copy(
            discussionDeadline = Instant.now().minusSeconds(1),
            status = com.commonsengine.governance.domain.ProposalStatus.VOTING,
        )

        service.castVote(proposal.id, "voter-1", StakeholderType.WORKER, VoteChoice.YES)
        assertThrows<IllegalArgumentException> {
            service.castVote(proposal.id, "voter-1", StakeholderType.WORKER, VoteChoice.NO)
        }
    }

    @Test
    fun `charter amendment requires two thirds majority`() {
        val proposal = service.createProposal("修宪", "描述", "member-1", ProposalType.CHARTER_AMENDMENT)
        val govField = GovernanceService::class.java.getDeclaredField("proposals")
        govField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val proposalsMap = govField.get(service) as ConcurrentHashMap<String, com.commonsengine.governance.domain.Proposal>
        proposalsMap[proposal.id.value] = proposal.copy(
            discussionDeadline = Instant.now().minusSeconds(1),
            status = com.commonsengine.governance.domain.ProposalStatus.VOTING,
        )

        // 赞成 60%（简单多数但不到 2/3）
        service.castVote(proposal.id, "w1", StakeholderType.WORKER, VoteChoice.YES)    // 0.4
        service.castVote(proposal.id, "c1", StakeholderType.CONSUMER, VoteChoice.YES)  // 0.3
        service.castVote(proposal.id, "cm1", StakeholderType.COMMUNITY, VoteChoice.NO) // 0.3

        val result = service.tallyVotes(proposal.id)
        assertFalse(result.passed, "修宪需 2/3 多数，60% 不够")
    }
}
