package com.commonsengine.governance.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 治理领域模型（#69 治理模块）
 *
 * 公地引擎治理域——提案、投票、加权计票。
 * 利益相关者权重体现平台治理哲学：劳动者 40%、消费者 30%、社区 30%。
 */
public final class Model {

    private Model() {
    }

    /**
     * 利益相关者类型——投票权重。
     *
     * 权重分配体现「劳动优先」的治理原则：
     * - WORKER 0.40：劳动者是平台价值的主要创造者
     * - CONSUMER 0.30：消费者是平台服务的直接受益者
     * - COMMUNITY 0.30：社区代表公共利益与长期可持续性
     */
    public enum StakeholderType {
        WORKER(0.40),
        CONSUMER(0.30),
        COMMUNITY(0.30);

        private final double weight;

        StakeholderType(double weight) {
            this.weight = weight;
        }

        public double getWeight() {
            return weight;
        }
    }

    /** 提案状态 */
    public enum ProposalStatus {
        DISCUSSION,     // 讨论期
        VOTING,         // 投票期
        PASSED,         // 通过
        REJECTED,       // 否决
        IMPLEMENTED     // 已实施
    }

    /** 提案类型 */
    public enum ProposalType {
        POLICY_CHANGE,      // 规则变更
        FEE_ADJUSTMENT,     // 费率调整
        FEATURE_REQUEST,    // 功能需求
        PLATFORM_RULE       // 平台规则
    }

    /** 投票选择 */
    public enum VoteChoice {
        APPROVE,    // 赞成
        REJECT,     // 反对
        ABSTAIN     // 弃权
    }

    /** 提案 ID */
    public record ProposalId(String value) {
        public ProposalId {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("ProposalId 不能为空");
            }
        }

        public static ProposalId random() {
            return new ProposalId(UUID.randomUUID().toString());
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /** 提案 */
    public record Proposal(
            ProposalId id,
            String title,
            String description,
            ProposalType type,
            String proposerId,
            ProposalStatus status,
            Instant createdAt,
            Instant votingOpensAt,
            Instant votingClosesAt,
            long discussionDurationHours,
            long votingDurationHours
    ) {
        public Proposal {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("提案标题不能为空");
            }
        }

        /**
         * 判断当前时间是否在投票期内。
         */
        public boolean isVotingOpen(Instant now) {
            return votingOpensAt != null && votingClosesAt != null
                    && !now.isBefore(votingOpensAt) && now.isBefore(votingClosesAt);
        }

        /**
         * 判断讨论期是否已满。
         */
        public boolean isDiscussionOver(Instant now) {
            return votingOpensAt != null && !now.isBefore(votingOpensAt);
        }
    }

    /** 投票 */
    public record Vote(
            String id,
            ProposalId proposalId,
            String voterId,
            StakeholderType stakeholderType,
            VoteChoice choice,
            String reason,
            Instant castAt
    ) {
        public Vote {
            if (voterId == null || voterId.isBlank()) {
                throw new IllegalArgumentException("voterId 不能为空");
            }
            if (proposalId == null) {
                throw new IllegalArgumentException("proposalId 不能为空");
            }
        }

        public static Vote create(ProposalId proposalId, String voterId,
                                   StakeholderType stakeholderType, VoteChoice choice,
                                   String reason) {
            return new Vote(
                    UUID.randomUUID().toString(),
                    proposalId,
                    voterId,
                    stakeholderType,
                    choice,
                    reason,
                    Instant.now()
            );
        }
    }

    /**
     * 投票计票结果——按利益相关者类型加权。
     *
     * 加权得分 = Σ(每票选择权重 × 利益相关者权重)
     * APPROVE 计正分，REJECT 计负分，ABSTAIN 计零分。
     * 最终得分 > 0 视为通过。
     */
    public record VoteResult(
            ProposalId proposalId,
            int totalVotes,
            int approveCount,
            int rejectCount,
            int abstainCount,
            double weightedScore,
            boolean passed
    ) {
    }
}
