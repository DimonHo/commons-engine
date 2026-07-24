package com.commonsengine.dispute.domain;

import com.commonsengine.platform.exception.BusinessRuleException;

import java.time.Instant;
import java.util.UUID;

/**
 * 纠纷领域模型（#65）
 *
 * 公地引擎纠纷工单领域，从 Kotlin data class 迁移至 Java record + class。
 * 纠纷类型、优先级、状态用枚举表达；领域实体 Dispute 用可变 class（因状态机演进）。
 */
public final class Model {

    private Model() {
    }

    /** 纠纷类型 */
    public enum DisputeType {
        PAYMENT_DISPUTE,    // 支付纠纷
        SERVICE_QUALITY,    // 服务质量
        CANCELLATION,       // 取消订单
        SAFETY_INCIDENT,    // 安全事件
        OTHER
    }

    /** 纠纷优先级——影响 SLA 与处理顺序 */
    public enum DisputePriority {
        LOW(24), MEDIUM(8), HIGH(2), URGENT(1);

        /** 目标响应时限（小时） */
        private final int slaHours;

        DisputePriority(int slaHours) {
            this.slaHours = slaHours;
        }

        public int getSlaHours() {
            return slaHours;
        }
    }

    /** 纠纷状态——状态机驱动流转 */
    public enum DisputeStatus {
        FILED,              // 已提交
        AI_SCREENING,       // AI 预审中
        UNDER_REVIEW,       // 人工审核中
        ARBITRATING,        // 仲裁中
        RESOLVED,           // 已解决
        REJECTED            // 已驳回
    }

    /** 仲裁裁决类型 */
    public enum VerdictType {
        REFUND_FULL,        // 全额退款
        REFUND_PARTIAL,     // 部分退款
        REWORK,             // 返工/重做
        COMPENSATION,       // 额外补偿
        DISMISS             // 驳回（维持原交易）
    }

    /** 纠纷 ID——@JvmInline value class → record with static random() */
    public record DisputeId(String value) {
        public DisputeId {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("DisputeId 不能为空");
            }
        }

        public static DisputeId random() {
            return new DisputeId(UUID.randomUUID().toString());
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /** AI 预审结果 */
    public record AiScreeningResult(
            double confidence,
            String category,
            String summary,
            boolean needsHumanReview
    ) {
    }

    /** 仲裁裁决 */
    public record ArbitrationVerdict(
            VerdictType type,
            double amount,
            String reason,
            Instant decidedAt
    ) {
    }

    /**
     * 纠纷工单——可变领域实体。
     *
     * 状态机：FILED → AI_SCREENING → UNDER_REVIEW → ARBITRATING → RESOLVED/REJECTED
     */
    public static final class Dispute {
        private final DisputeId id;
        private String transactionId;
        private String consumerId;
        private String workerId;
        private final DisputeType type;
        private DisputePriority priority;
        private final String description;
        private DisputeStatus status;
        private AiScreeningResult aiScreening;
        private ArbitrationVerdict verdict;
        private final Instant createdAt;
        private Instant resolvedAt;

        public Dispute(
                DisputeId id,
                String transactionId,
                String consumerId,
                String workerId,
                DisputeType type,
                DisputePriority priority,
                String description,
                DisputeStatus status,
                AiScreeningResult aiScreening,
                ArbitrationVerdict verdict,
                Instant createdAt,
                Instant resolvedAt
        ) {
            this.id = id;
            this.transactionId = transactionId;
            this.consumerId = consumerId;
            this.workerId = workerId;
            this.type = type;
            this.priority = priority;
            this.description = description;
            this.status = status;
            this.aiScreening = aiScreening;
            this.verdict = verdict;
            this.createdAt = createdAt;
            this.resolvedAt = resolvedAt;
        }

        public DisputeId getId() {
            return id;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public void setTransactionId(String transactionId) {
            this.transactionId = transactionId;
        }

        public String getConsumerId() {
            return consumerId;
        }

        public void setConsumerId(String consumerId) {
            this.consumerId = consumerId;
        }

        public String getWorkerId() {
            return workerId;
        }

        public void setWorkerId(String workerId) {
            this.workerId = workerId;
        }

        public DisputeType getType() {
            return type;
        }

        public DisputePriority getPriority() {
            return priority;
        }

        public void setPriority(DisputePriority priority) {
            this.priority = priority;
        }

        public String getDescription() {
            return description;
        }

        public DisputeStatus getStatus() {
            return status;
        }

        public void setStatus(DisputeStatus status) {
            this.status = status;
        }

        public AiScreeningResult getAiScreening() {
            return aiScreening;
        }

        public void setAiScreening(AiScreeningResult aiScreening) {
            this.aiScreening = aiScreening;
        }

        public ArbitrationVerdict getVerdict() {
            return verdict;
        }

        public void setVerdict(ArbitrationVerdict verdict) {
            this.verdict = verdict;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Instant getResolvedAt() {
            return resolvedAt;
        }

        public void setResolvedAt(Instant resolvedAt) {
            this.resolvedAt = resolvedAt;
        }

        /**
         * 校验状态流转是否允许。
         *
         * @throws BusinessRuleException 当前状态不允许跳转到目标状态
         */
        public void transitionTo(DisputeStatus target) {
            if (!isValidTransition(this.status, target)) {
                throw new BusinessRuleException(
                        "DISPUTE_INVALID_TRANSITION",
                        "纠纷工单状态不允许从 " + this.status + " 流转到 " + target
                );
            }
            this.status = target;
        }

        private static boolean isValidTransition(DisputeStatus from, DisputeStatus to) {
            if (from == to) {
                return true;
            }
            return switch (from) {
                case FILED -> to == DisputeStatus.AI_SCREENING || to == DisputeStatus.REJECTED;
                case AI_SCREENING -> to == DisputeStatus.UNDER_REVIEW || to == DisputeStatus.RESOLVED
                        || to == DisputeStatus.REJECTED;
                case UNDER_REVIEW -> to == DisputeStatus.ARBITRATING || to == DisputeStatus.RESOLVED
                        || to == DisputeStatus.REJECTED;
                case ARBITRATING -> to == DisputeStatus.RESOLVED || to == DisputeStatus.REJECTED;
                case RESOLVED, REJECTED -> false;
            };
        }
    }
}
