package com.commonsengine.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 支付与分账领域模型（payment 模块）。
 *
 * <p>核心概念：交易、分账规则、分账结果、不可篡改的账本事件。
 */
public final class Model {

    private Model() {
    }

    /**
     * 交易状态。
     */
    public enum TransactionStatus {
        /** 待支付 */
        PENDING,
        /** 已收款 */
        CHARGED,
        /** 已分账结算 */
        SETTLED,
        /** 已退款 */
        REFUNDED,
        /** 失败 */
        FAILED,
    }

    /**
     * 交易 ID（UUID 字符串）。
     */
    public record TransactionId(String value) {
        public TransactionId {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("TransactionId 不能为空");
            }
        }

        public static TransactionId random() {
            return new TransactionId(UUID.randomUUID().toString());
        }
    }

    /**
     * 交易订单。
     */
    public record Transaction(
            TransactionId id,
            String consumerId,
            String workerId,
            BigDecimal amount,
            String serviceType,
            Instant createdAt,
            TransactionStatus status
    ) {
        public Transaction {
            if (createdAt == null) {
                createdAt = Instant.now();
            }
            if (status == null) {
                status = TransactionStatus.PENDING;
            }
        }

        /** 返回状态变更后的副本（record 不可变，故返回新实例） */
        public Transaction withStatus(TransactionStatus newStatus) {
            return new Transaction(id, consumerId, workerId, amount, serviceType, createdAt, newStatus);
        }
    }

    /**
     * 分账规则——多少归劳动者、多少归运营成本、多少归公积金。
     *
     * <p>抽成比例、流向在交易时对劳动者和消费者完全可见（章程第 4.3 条）。
     *
     * <p>因含校验逻辑，使用普通类（非 record）。校验规则：
     * <ul>
     *   <li>三项比例之和必须为 1.0（100%）</li>
     *   <li>反榨取约束：劳动者所得不低于 70%（需全体大会最终确定）</li>
     * </ul>
     */
    public static final class SettlementRule {

        /** 默认分账规则：劳动者 80% / 运营 15% / 公积金 5% */
        public static final SettlementRule DEFAULT = new SettlementRule(
                new BigDecimal("0.80"),
                new BigDecimal("0.15"),
                new BigDecimal("0.05")
        );

        private final BigDecimal workerShareRate;
        private final BigDecimal platformOperationRate;
        private final BigDecimal commonsFundRate;

        public SettlementRule(BigDecimal workerShareRate,
                              BigDecimal platformOperationRate,
                              BigDecimal commonsFundRate) {
            BigDecimal total = workerShareRate.add(platformOperationRate).add(commonsFundRate);
            if (total.compareTo(BigDecimal.ONE) != 0) {
                throw new IllegalArgumentException(
                        "分账比例之和必须为 1.0（100%），实际: " + total);
            }
            // 反榨取约束：劳动者所得不低于 70%（需全体大会最终确定）
            if (workerShareRate.compareTo(new BigDecimal("0.70")) < 0) {
                throw new IllegalArgumentException(
                        "劳动者所得比例不得低于 70%（反榨取底线），实际: " + workerShareRate);
            }
            this.workerShareRate = workerShareRate;
            this.platformOperationRate = platformOperationRate;
            this.commonsFundRate = commonsFundRate;
        }

        public BigDecimal workerShareRate() {
            return workerShareRate;
        }

        public BigDecimal platformOperationRate() {
            return platformOperationRate;
        }

        public BigDecimal commonsFundRate() {
            return commonsFundRate;
        }
    }

    /**
     * 分账结果——资金流向明细。
     */
    public record SettlementResult(
            TransactionId transactionId,
            BigDecimal totalAmount,
            BigDecimal workerPayout,
            BigDecimal platformFee,
            BigDecimal commonsFund,
            SettlementRule rule,
            String breakdown
    ) {
    }

    /**
     * 账本事件——事件溯源模式，不可篡改的资金流水。
     *
     * <p>Java 密封接口，对应 Kotlin sealed class。
     * 三种事件：{@link ChargeCreated}、{@link SettlementCompleted}、{@link RefundIssued}。
     */
    public sealed interface LedgerEvent
            permits LedgerEvent.ChargeCreated,
                    LedgerEvent.SettlementCompleted,
                    LedgerEvent.RefundIssued {

        String eventId();

        TransactionId transactionId();

        Instant timestamp();

        /**
         * 收款事件——向消费者发起收款后产生。
         */
        record ChargeCreated(
                String eventId,
                TransactionId transactionId,
                Instant timestamp,
                String consumerId,
                String workerId,
                BigDecimal amount,
                String serviceType,
                String paymentChannel
        ) implements LedgerEvent {
        }

        /**
         * 分账完成事件——资金已按规则分配并打款后产生。
         */
        record SettlementCompleted(
                String eventId,
                TransactionId transactionId,
                Instant timestamp,
                BigDecimal workerPayout,
                BigDecimal platformFee,
                BigDecimal commonsFund
        ) implements LedgerEvent {
        }

        /**
         * 退款事件——退款成功后产生。
         */
        record RefundIssued(
                String eventId,
                TransactionId transactionId,
                Instant timestamp,
                BigDecimal refundAmount,
                String reason
        ) implements LedgerEvent {
        }
    }
}
