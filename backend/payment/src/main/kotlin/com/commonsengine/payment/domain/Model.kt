package com.commonsengine.payment.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * 交易订单
 */
data class Transaction(
    val id: TransactionId,
    val consumerId: String,
    val workerId: String,
    val amount: BigDecimal,
    val serviceType: String,
    val createdAt: Instant = Instant.now(),
    val status: TransactionStatus = TransactionStatus.PENDING,
)

@JvmInline
value class TransactionId(val value: String) {
    companion object { fun random() = TransactionId(UUID.randomUUID().toString()) }
}

enum class TransactionStatus {
    PENDING,        // 待支付
    CHARGED,        // 已收款
    SETTLED,        // 已分账结算
    REFUNDED,       // 已退款
    FAILED,         // 失败
}

/**
 * 分账规则——多少归劳动者、多少归运营成本、多少归公积金
 *
 * 抽成比例、流向在交易时对劳动者和消费者完全可见（章程第 4.3 条）。
 */
data class SettlementRule(
    val workerShareRate: BigDecimal,       // 劳动者所得比例
    val platformOperationRate: BigDecimal, // 平台运营成本比例
    val commonsFundRate: BigDecimal,        // 公积金比例
) {
    init {
        val total = workerShareRate + platformOperationRate + commonsFundRate
        require(total.compareTo(BigDecimal.ONE) == 0) {
            "分账比例之和必须为 1.0（100%），实际: $total"
        }
        // 反榨取约束：劳动者所得不低于 70%（需全体大会最终确定）
        require(workerShareRate.compareTo(BigDecimal("0.70")) >= 0) {
            "劳动者所得比例不得低于 70%（反榨取底线），实际: $workerShareRate"
        }
    }

    companion object {
        /** 默认分账规则：劳动者 80% / 运营 15% / 公积金 5% */
        val DEFAULT = SettlementRule(
            workerShareRate = BigDecimal("0.80"),
            platformOperationRate = BigDecimal("0.15"),
            commonsFundRate = BigDecimal("0.05"),
        )
    }
}

/**
 * 分账结果——资金流向明细
 */
data class SettlementResult(
    val transactionId: TransactionId,
    val totalAmount: BigDecimal,
    val workerPayout: BigDecimal,
    val platformFee: BigDecimal,
    val commonsFund: BigDecimal,
    val rule: SettlementRule,
    val breakdown: String,   // 人类可读的费用拆分
)

/**
 * 账本事件——事件溯源模式，不可篡改的资金流水
 */
sealed class LedgerEvent {
    abstract val eventId: String
    abstract val transactionId: TransactionId
    abstract val timestamp: Instant

    data class ChargeCreated(
        override val eventId: String,
        override val transactionId: TransactionId,
        override val timestamp: Instant,
        val consumerId: String,
        val amount: BigDecimal,
        val paymentChannel: String,
    ) : LedgerEvent()

    data class SettlementCompleted(
        override val eventId: String,
        override val transactionId: TransactionId,
        override val timestamp: Instant,
        val workerPayout: BigDecimal,
        val platformFee: BigDecimal,
        val commonsFund: BigDecimal,
    ) : LedgerEvent()

    data class RefundIssued(
        override val eventId: String,
        override val transactionId: TransactionId,
        override val timestamp: Instant,
        val refundAmount: BigDecimal,
        val reason: String,
    ) : LedgerEvent()
}
