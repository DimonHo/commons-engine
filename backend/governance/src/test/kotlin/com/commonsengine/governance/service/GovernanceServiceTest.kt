package com.commonsengine.governance.service

import com.commonsengine.governance.domain.ProposalStatus
import com.commonsengine.governance.domain.ProposalType
import com.commonsengine.governance.domain.StakeholderType
import com.commonsengine.governance.domain.VoteChoice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GovernanceServiceTest {

    @Autowired
    private lateinit var service: GovernanceService

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
        // Create proposal with past deadline directly via repository
        val proposal = service.createProposal("测试投票", "描述", "member-1")
        // Use the service's internal method to set past deadline by creating a proposal
        // then starting vote after deadline
        // Since we can't directly modify the entity, we test with a proposal
        // whose deadline is manually set via the repository

        // For this test, we need to bypass the discussion deadline check.
        // We'll use the repository directly to set a past deadline.
        val proposalRepo = service.javaClass.getDeclaredField("proposalRepository")
        proposalRepo.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val repo = proposalRepo.get(service) as com.commonsengine.governance.infrastructure.persistence.ProposalRepository

        val entity = repo.findByProposalId(proposal.id.value)!!
        entity.discussionDeadline = Instant.now().minus(1, ChronoUnit.DAYS)
        repo.save(entity)

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

        // Set past deadline and start voting
        val proposalRepo = service.javaClass.getDeclaredField("proposalRepository")
        proposalRepo.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val repo = proposalRepo.get(service) as com.commonsengine.governance.infrastructure.persistence.ProposalRepository

        val entity = repo.findByProposalId(proposal.id.value)!!
        entity.discussionDeadline = Instant.now().minusSeconds(1)
        entity.status = ProposalStatus.VOTING
        repo.save(entity)

        service.castVote(proposal.id, "voter-1", StakeholderType.WORKER, VoteChoice.YES)
        assertThrows<IllegalArgumentException> {
            service.castVote(proposal.id, "voter-1", StakeholderType.WORKER, VoteChoice.NO)
        }
    }

    @Test
    fun `charter amendment requires two thirds majority`() {
        val proposal = service.createProposal("修宪", "描述", "member-1", ProposalType.CHARTER_AMENDMENT)

        val proposalRepo = service.javaClass.getDeclaredField("proposalRepository")
        proposalRepo.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val repo = proposalRepo.get(service) as com.commonsengine.governance.infrastructure.persistence.ProposalRepository

        val entity = repo.findByProposalId(proposal.id.value)!!
        entity.discussionDeadline = Instant.now().minusSeconds(1)
        entity.status = ProposalStatus.VOTING
        repo.save(entity)

        // 赞成 60%（简单多数但不到 2/3）
        service.castVote(proposal.id, "c1", StakeholderType.CONSUMER, VoteChoice.YES)  // 0.3
        service.castVote(proposal.id, "cm1", StakeholderType.COMMUNITY, VoteChoice.YES) // 0.3
        service.castVote(proposal.id, "w1", StakeholderType.WORKER, VoteChoice.NO)    // 0.4

        val result = service.tallyVotes(proposal.id)
        assertFalse(result.passed, "修宪需 2/3 多数，60% 不够")
    }

    // ── 持久化测试 ─────────────────────────────────────

    @Test
    fun `create proposal persists and can be found`() {
        val proposal = service.createProposal("持久化测试", "测试描述", "member-persist")

        val found = service.findProposal(proposal.id)
        assertNotNull(found)
        assertEquals("持久化测试", found!!.title)
        assertEquals("测试描述", found.description)
        assertEquals("member-persist", found.proposedBy)
        assertEquals(ProposalStatus.DISCUSSION, found.status)
    }

    @Test
    fun `find non-existent proposal returns null`() {
        val found = service.findProposal(com.commonsengine.governance.domain.ProposalId("no-such-proposal"))
        assertNull(found)
    }

    @Test
    fun `find all proposals returns persisted list`() {
        service.createProposal("提案A", "描述A", "member-1")
        service.createProposal("提案B", "描述B", "member-2")

        val all = service.findAllProposals()
        assertTrue(all.size >= 2)
        assertTrue(all.any { it.title == "提案A" })
        assertTrue(all.any { it.title == "提案B" })
    }

    @Test
    fun `tally votes updates proposal status to approved`() {
        val proposal = service.createProposal("通过测试", "描述", "member-1")

        val proposalRepo = service.javaClass.getDeclaredField("proposalRepository")
        proposalRepo.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val repo = proposalRepo.get(service) as com.commonsengine.governance.infrastructure.persistence.ProposalRepository

        val entity = repo.findByProposalId(proposal.id.value)!!
        entity.discussionDeadline = Instant.now().minusSeconds(1)
        entity.status = ProposalStatus.VOTING
        repo.save(entity)

        // 劳动者赞成（0.4）+ 消费者赞成（0.3）= 0.7 > 0.5
        service.castVote(proposal.id, "w1", StakeholderType.WORKER, VoteChoice.YES)
        service.castVote(proposal.id, "c1", StakeholderType.CONSUMER, VoteChoice.YES)

        service.tallyVotes(proposal.id)

        val found = service.findProposal(proposal.id)!!
        assertEquals(ProposalStatus.APPROVED, found.status)
    }

    @Test
    fun `tally votes updates proposal status to rejected`() {
        val proposal = service.createProposal("否决测试", "描述", "member-1")

        val proposalRepo = service.javaClass.getDeclaredField("proposalRepository")
        proposalRepo.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val repo = proposalRepo.get(service) as com.commonsengine.governance.infrastructure.persistence.ProposalRepository

        val entity = repo.findByProposalId(proposal.id.value)!!
        entity.discussionDeadline = Instant.now().minusSeconds(1)
        entity.status = ProposalStatus.VOTING
        repo.save(entity)

        // 只有劳动者反对（0.4），无人赞成 → 0% < 50%
        service.castVote(proposal.id, "w1", StakeholderType.WORKER, VoteChoice.NO)

        service.tallyVotes(proposal.id)

        val found = service.findProposal(proposal.id)!!
        assertEquals(ProposalStatus.REJECTED, found.status)
    }

    @Test
    fun `start vote transitions status to voting`() {
        val proposal = service.createProposal("投票状态测试", "描述", "member-1")

        val proposalRepo = service.javaClass.getDeclaredField("proposalRepository")
        proposalRepo.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val repo = proposalRepo.get(service) as com.commonsengine.governance.infrastructure.persistence.ProposalRepository

        val entity = repo.findByProposalId(proposal.id.value)!!
        entity.discussionDeadline = Instant.now().minus(1, ChronoUnit.DAYS)
        repo.save(entity)

        val updated = service.startVote(proposal.id)
        assertNotNull(updated)
        assertEquals(ProposalStatus.VOTING, updated!!.status)
    }

    @Test
    fun `proposal type persists correctly`() {
        val proposal = service.createProposal("预算提案", "描述", "member-1", ProposalType.BUDGET_ALLOCATION)

        val found = service.findProposal(proposal.id)!!
        assertEquals(ProposalType.BUDGET_ALLOCATION, found.type)
    }
}
