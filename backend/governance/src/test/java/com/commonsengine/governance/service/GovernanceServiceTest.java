package com.commonsengine.governance.service;

import com.commonsengine.governance.domain.Model.Proposal;
import com.commonsengine.governance.domain.Model.ProposalId;
import com.commonsengine.governance.domain.Model.ProposalStatus;
import com.commonsengine.governance.domain.Model.ProposalType;
import com.commonsengine.governance.domain.Model.StakeholderType;
import com.commonsengine.governance.domain.Model.Vote;
import com.commonsengine.governance.domain.Model.VoteChoice;
import com.commonsengine.governance.domain.Model.VoteResult;
import com.commonsengine.governance.infrastructure.persistence.GovernancePersistence.ProposalEntity;
import com.commonsengine.governance.infrastructure.persistence.GovernanceRepositories.ProposalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 治理服务测试——从 Kotlin GovernanceServiceTest 转换。
 *
 * <p>适配 Java GovernanceService API：
 * <ul>
 *   <li>{@code createProposal(title, description, type, proposerId)}（默认讨论期 72h、投票期 48h）</li>
 *   <li>{@code openVoting(id)}——讨论期满后开启投票</li>
 *   <li>{@code castVote(proposalId, voterId, stakeholderType, choice, reason)}</li>
 *   <li>{@code tallyAndResolve(id)}——加权计票并决议</li>
 * </ul>
 *
 * <p>权重：WORKER 0.40 / CONSUMER 0.30 / COMMUNITY 0.30；得分 > 0 即通过。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GovernanceServiceTest {

    @Autowired
    private GovernanceService service;

    @Autowired
    private ProposalRepository proposalRepository;

    @Test
    void createProposalSetsDefaultDiscussionPeriod() {
        Proposal proposal = service.createProposal("调整分账比例", "描述",
                ProposalType.FEE_ADJUSTMENT, "member-1");
        long daysUntilVotingOpens = ChronoUnit.DAYS.between(
                Instant.now(), proposal.votingOpensAt());
        // 默认讨论期 72h ≈ 3 天
        assertTrue(daysUntilVotingOpens >= 2 && daysUntilVotingOpens <= 3,
                "讨论期应约 3 天（72h），实际: " + daysUntilVotingOpens);
    }

    @Test
    void createProposalSetsVotingClosesAfterVotingPeriod() {
        Proposal proposal = service.createProposal("修改章程", "描述",
                ProposalType.PLATFORM_RULE, "member-1");
        long hoursBetween = ChronoUnit.HOURS.between(
                proposal.votingOpensAt(), proposal.votingClosesAt());
        // 投票期默认 48h
        assertEquals(48, hoursBetween, "投票期应为 48 小时");
    }

    @Test
    void cannotStartVoteBeforeDiscussionDeadline() {
        Proposal proposal = service.createProposal("测试", "描述",
                ProposalType.POLICY_CHANGE, "member-1");
        // 讨论期未满，开启投票应失败
        assertThrows(Exception.class, () -> service.openVoting(proposal.id()));
    }

    @Test
    void weightedVotingWorksCorrectly() {
        Proposal proposal = service.createProposal("测试投票", "描述",
                ProposalType.POLICY_CHANGE, "member-1");

        // 直接将讨论截止时间设为过去，然后开启投票
        forceDiscussionOver(proposal.id());

        service.openVoting(proposal.id());

        // 投票：2 个劳动者赞成（+0.4 +0.4），1 个消费者反对（−0.3）
        service.castVote(proposal.id(), "w1", StakeholderType.WORKER, VoteChoice.APPROVE, null);
        service.castVote(proposal.id(), "w2", StakeholderType.WORKER, VoteChoice.APPROVE, null);
        service.castVote(proposal.id(), "c1", StakeholderType.CONSUMER, VoteChoice.REJECT, null);

        // 推进投票期到结束后再计票
        forceVotingOver(proposal.id());

        VoteResult result = service.tallyAndResolve(proposal.id());

        // +0.8 − 0.3 = +0.5 > 0 → 通过
        assertTrue(result.passed(), "赞成权重 0.8 > 反对 0.3，应通过");
        assertEquals(0.5, result.weightedScore(), 0.001);
    }

    @Test
    void onePersonOneVote() {
        Proposal proposal = service.createProposal("测试", "描述",
                ProposalType.POLICY_CHANGE, "member-1");

        forceDiscussionOver(proposal.id());
        service.openVoting(proposal.id());

        service.castVote(proposal.id(), "voter-1", StakeholderType.WORKER,
                VoteChoice.APPROVE, null);
        // 同一投票者再次投票应被拒绝
        assertThrows(Exception.class, () ->
                service.castVote(proposal.id(), "voter-1", StakeholderType.WORKER,
                        VoteChoice.REJECT, null));
    }

    @Test
    void weightedScoreRejectsWhenOppositionOutweighs() {
        Proposal proposal = service.createProposal("否决议案", "描述",
                ProposalType.POLICY_CHANGE, "member-1");

        forceDiscussionOver(proposal.id());
        service.openVoting(proposal.id());

        // 只有劳动者反对（−0.4），无人赞成 → 得分 −0.4 < 0 → 否决
        service.castVote(proposal.id(), "w1", StakeholderType.WORKER,
                VoteChoice.REJECT, null);

        forceVotingOver(proposal.id());
        VoteResult result = service.tallyAndResolve(proposal.id());

        assertFalse(result.passed(), "反对权重 0.4 > 0，应否决");
    }

    // ── 持久化测试 ─────────────────────────────────────

    @Test
    void createProposalPersistsAndCanBeFound() {
        Proposal proposal = service.createProposal("持久化测试", "测试描述",
                ProposalType.FEATURE_REQUEST, "member-persist");

        Proposal found = service.findById(proposal.id());
        assertNotNull(found);
        assertEquals("持久化测试", found.title());
        assertEquals("测试描述", found.description());
        assertEquals("member-persist", found.proposerId());
        assertEquals(ProposalStatus.DISCUSSION, found.status());
    }

    @Test
    void findByStatusReturnsPersistedList() {
        service.createProposal("提案A", "描述A", ProposalType.POLICY_CHANGE, "member-1");
        service.createProposal("提案B", "描述B", ProposalType.FEE_ADJUSTMENT, "member-2");

        List<Proposal> discussion = service.findByStatus(ProposalStatus.DISCUSSION);
        assertTrue(discussion.size() >= 2);
        assertTrue(discussion.stream().anyMatch(p -> p.title().equals("提案A")));
        assertTrue(discussion.stream().anyMatch(p -> p.title().equals("提案B")));
    }

    @Test
    void tallyVotesUpdatesProposalStatusToPassed() {
        Proposal proposal = service.createProposal("通过测试", "描述",
                ProposalType.POLICY_CHANGE, "member-1");

        forceDiscussionOver(proposal.id());
        service.openVoting(proposal.id());

        // 劳动者赞成（+0.4）+ 消费者赞成（+0.3）= +0.7 > 0
        service.castVote(proposal.id(), "w1", StakeholderType.WORKER,
                VoteChoice.APPROVE, null);
        service.castVote(proposal.id(), "c1", StakeholderType.CONSUMER,
                VoteChoice.APPROVE, null);

        forceVotingOver(proposal.id());
        service.tallyAndResolve(proposal.id());

        Proposal found = service.findById(proposal.id());
        assertEquals(ProposalStatus.PASSED, found.status());
    }

    @Test
    void tallyVotesUpdatesProposalStatusToRejected() {
        Proposal proposal = service.createProposal("否决测试", "描述",
                ProposalType.POLICY_CHANGE, "member-1");

        forceDiscussionOver(proposal.id());
        service.openVoting(proposal.id());

        // 只有劳动者反对（−0.4），无人赞成
        service.castVote(proposal.id(), "w1", StakeholderType.WORKER,
                VoteChoice.REJECT, null);

        forceVotingOver(proposal.id());
        service.tallyAndResolve(proposal.id());

        Proposal found = service.findById(proposal.id());
        assertEquals(ProposalStatus.REJECTED, found.status());
    }

    @Test
    void openVotingTransitionsStatusToVoting() {
        Proposal proposal = service.createProposal("投票状态测试", "描述",
                ProposalType.POLICY_CHANGE, "member-1");

        forceDiscussionOver(proposal.id());
        Proposal updated = service.openVoting(proposal.id());

        assertNotNull(updated);
        assertEquals(ProposalStatus.VOTING, updated.status());
    }

    @Test
    void proposalTypePersistsCorrectly() {
        Proposal proposal = service.createProposal("预算提案", "描述",
                ProposalType.FEE_ADJUSTMENT, "member-1");

        Proposal found = service.findById(proposal.id());
        assertEquals(ProposalType.FEE_ADJUSTMENT, found.type());
    }

    @Test
    void findVotesReturnsCastVotes() {
        Proposal proposal = service.createProposal("查票测试", "描述",
                ProposalType.POLICY_CHANGE, "member-1");

        forceDiscussionOver(proposal.id());
        service.openVoting(proposal.id());
        service.castVote(proposal.id(), "w1", StakeholderType.WORKER,
                VoteChoice.APPROVE, "支持");

        List<Vote> votes = service.findVotes(proposal.id());
        assertEquals(1, votes.size());
        assertEquals("w1", votes.get(0).voterId());
    }

    // ── 辅助方法 ───────────────────────────────────────

    /**
     * 直接通过 repository 将提案的投票开放时间设为过去，使讨论期「已满」。
     */
    private void forceDiscussionOver(ProposalId id) {
        ProposalEntity entity = proposalRepository.findById(id.value()).orElseThrow();
        entity.setVotingOpensAt(Instant.now().minusSeconds(1));
        proposalRepository.save(entity);
    }

    /**
     * 直接通过 repository 将提案的投票关闭时间设为过去，使投票期「已结束」。
     */
    private void forceVotingOver(ProposalId id) {
        ProposalEntity entity = proposalRepository.findById(id.value()).orElseThrow();
        entity.setVotingClosesAt(Instant.now().minusSeconds(1));
        proposalRepository.save(entity);
    }
}
