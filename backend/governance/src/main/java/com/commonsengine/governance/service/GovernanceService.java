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
import com.commonsengine.governance.infrastructure.persistence.GovernancePersistence.VoteEntity;
import com.commonsengine.governance.infrastructure.persistence.GovernancePersistence.ProposalMapper;
import com.commonsengine.governance.infrastructure.persistence.GovernancePersistence.VoteMapper;
import com.commonsengine.governance.infrastructure.persistence.GovernanceRepositories.ProposalRepository;
import com.commonsengine.governance.infrastructure.persistence.GovernanceRepositories.VoteRepository;
import com.commonsengine.platform.exception.BusinessRuleException;
import com.commonsengine.platform.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 治理业务服务（#69 治理模块）
 *
 * 职责：
 * 1. 创建提案（讨论期 → 投票期 → 决议）
 * 2. 开启投票（讨论期满后）
 * 3. 投票（投票期内，一人一票）
 * 4. 计票（加权：WORKER 0.40 / CONSUMER 0.30 / COMMUNITY 0.30）
 * 5. 关闭投票并决议
 *
 * 治理原则（对齐架构文档「民主治理 1.4」）：
 * - 利益相关者加权投票，非简单多数
 * - 提案有强制讨论期，防止冲动决议
 */
@Service
public class GovernanceService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceService.class);

    /** 默认讨论期（小时） */
    private static final long DEFAULT_DISCUSSION_HOURS = 72;
    /** 默认投票期（小时） */
    private static final long DEFAULT_VOTING_HOURS = 48;

    private final ProposalRepository proposalRepository;
    private final VoteRepository voteRepository;

    public GovernanceService(ProposalRepository proposalRepository,
                              VoteRepository voteRepository) {
        this.proposalRepository = proposalRepository;
        this.voteRepository = voteRepository;
    }

    @Transactional
    public Proposal createProposal(String title, String description, ProposalType type,
                                    String proposerId) {
        Instant now = Instant.now();
        Proposal proposal = new Proposal(
                ProposalId.random(),
                title,
                description,
                type,
                proposerId,
                ProposalStatus.DISCUSSION,
                now,
                now.plus(Duration.ofHours(DEFAULT_DISCUSSION_HOURS)),
                now.plus(Duration.ofHours(DEFAULT_DISCUSSION_HOURS + DEFAULT_VOTING_HOURS)),
                DEFAULT_DISCUSSION_HOURS,
                DEFAULT_VOTING_HOURS
        );
        ProposalEntity saved = proposalRepository.save(ProposalMapper.toEntity(proposal));
        log.info("提案已创建 id={} type={}", proposal.id(), type);
        return ProposalMapper.toDomain(saved);
    }

    /**
     * 投票——必须在投票期内，且一人一票。
     */
    @Transactional
    public Vote castVote(ProposalId proposalId, String voterId,
                          StakeholderType stakeholderType, VoteChoice choice, String reason) {
        Proposal proposal = loadProposalOrThrow(proposalId);
        Instant now = Instant.now();

        if (proposal.status() != ProposalStatus.VOTING) {
            if (proposal.status() == ProposalStatus.DISCUSSION) {
                throw new BusinessRuleException(
                        "PROPOSAL_STILL_IN_DISCUSSION",
                        "提案仍在讨论期，不能投票。投票开放时间: " + proposal.votingOpensAt()
                );
            }
            throw new BusinessRuleException(
                    "PROPOSAL_NOT_VOTABLE",
                    "提案状态为 " + proposal.status() + "，不接受投票"
            );
        }
        if (!proposal.isVotingOpen(now)) {
            throw new BusinessRuleException(
                    "VOTING_CLOSED",
                    "投票期已结束"
            );
        }

        // 一人一票校验
        if (!voteRepository.findByProposalIdAndVoterId(proposalId.value(), voterId).isEmpty()) {
            throw new BusinessRuleException(
                    "ALREADY_VOTED",
                    "投票者 " + voterId + " 已对此提案投过票"
            );
        }

        Vote vote = Vote.create(proposalId, voterId, stakeholderType, choice, reason);
        VoteEntity saved = voteRepository.save(VoteMapper.toEntity(vote));
        log.info("投票已记录 proposal={} voter={} choice={}", proposalId, voterId, choice);
        return VoteMapper.toDomain(saved);
    }

    /**
     * 开启投票——讨论期满后手动触发，或自动在计票时检查。
     */
    @Transactional
    public Proposal openVoting(ProposalId proposalId) {
        Proposal proposal = loadProposalOrThrow(proposalId);
        if (proposal.status() != ProposalStatus.DISCUSSION) {
            throw new BusinessRuleException(
                    "PROPOSAL_NOT_IN_DISCUSSION",
                    "提案状态为 " + proposal.status() + "，不在讨论期"
            );
        }
        if (!proposal.isDiscussionOver(Instant.now())) {
            throw new BusinessRuleException(
                    "DISCUSSION_NOT_OVER",
                    "讨论期未满，投票开放时间: " + proposal.votingOpensAt()
            );
        }

        Proposal updated = new Proposal(
                proposal.id(), proposal.title(), proposal.description(), proposal.type(),
                proposal.proposerId(), ProposalStatus.VOTING, proposal.createdAt(),
                proposal.votingOpensAt(), proposal.votingClosesAt(),
                proposal.discussionDurationHours(), proposal.votingDurationHours()
        );
        ProposalEntity saved = proposalRepository.save(ProposalMapper.toEntity(updated));
        log.info("提案进入投票期 id={}", proposalId);
        return ProposalMapper.toDomain(saved);
    }

    /**
     * 计票并决议——投票期结束后触发。
     *
     * 加权计票：APPROVE +权重，REJECT −权重，ABSTAIN 不计。
     * 最终得分 > 0 视为通过。
     */
    @Transactional
    public VoteResult tallyAndResolve(ProposalId proposalId) {
        Proposal proposal = loadProposalOrThrow(proposalId);
        if (proposal.status() != ProposalStatus.VOTING) {
            throw new BusinessRuleException(
                    "PROPOSAL_NOT_IN_VOTING",
                    "提案状态为 " + proposal.status() + "，不在投票期"
            );
        }

        List<VoteEntity> voteEntities = voteRepository.findByProposalId(proposalId.value());
        int approve = 0, reject = 0, abstain = 0;
        double weightedScore = 0;

        for (VoteEntity ve : voteEntities) {
            double weight = ve.getStakeholderType().getWeight();
            switch (ve.getChoice()) {
                case APPROVE -> {
                    approve++;
                    weightedScore += weight;
                }
                case REJECT -> {
                    reject++;
                    weightedScore -= weight;
                }
                case ABSTAIN -> abstain++;
            }
        }

        boolean passed = weightedScore > 0;
        ProposalStatus finalStatus = passed ? ProposalStatus.PASSED : ProposalStatus.REJECTED;

        Proposal resolved = new Proposal(
                proposal.id(), proposal.title(), proposal.description(), proposal.type(),
                proposal.proposerId(), finalStatus, proposal.createdAt(),
                proposal.votingOpensAt(), proposal.votingClosesAt(),
                proposal.discussionDurationHours(), proposal.votingDurationHours()
        );
        proposalRepository.save(ProposalMapper.toEntity(resolved));

        VoteResult result = new VoteResult(
                proposalId, voteEntities.size(), approve, reject, abstain, weightedScore, passed
        );
        log.info("提案计票完成 id={} total={} approve={} reject={} abstain={} score={} passed={}",
                proposalId, voteEntities.size(), approve, reject, abstain, weightedScore, passed);
        return result;
    }

    @Transactional(readOnly = true)
    public Proposal findById(ProposalId id) {
        return loadProposalOrThrow(id);
    }

    @Transactional(readOnly = true)
    public List<Proposal> findByStatus(ProposalStatus status) {
        return proposalRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(ProposalMapper::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Vote> findVotes(ProposalId proposalId) {
        return voteRepository.findByProposalId(proposalId.value()).stream()
                .map(VoteMapper::toDomain)
                .toList();
    }

    private Proposal loadProposalOrThrow(ProposalId id) {
        return proposalRepository.findById(id.value())
                .map(ProposalMapper::toDomain)
                .orElseThrow(() -> new NotFoundException("Proposal", id.value()));
    }
}
