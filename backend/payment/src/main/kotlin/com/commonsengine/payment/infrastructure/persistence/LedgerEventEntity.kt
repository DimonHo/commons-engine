package com.commonsengine.payment.infrastructure.persistence

import com.commonsengine.payment.domain.LedgerEvent
import com.commonsengine.payment.domain.TransactionId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * 账本事件 JPA 实体——不可篡改的资金流水
 *
 * 单表设计，用 event_type 区分三种事件（ChargeCreated/SettlementCompleted/RefundIssued）。
 * 对应领域模型 LedgerEvent sealed class 的三个子类。
 * Append-only: 只插入，不修改不删除。
 */
@Entity
@Table(name = "ledger_events")
class LedgerEventEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "event_id", nullable = false, length = 36)
    val eventId: String,

    @Column(name = "event_type", nullable = false, length = 30)
    val eventType: String,

    @Column(name = "transaction_id", nullable = false, length = 36)
    val transactionId: String,

    @Column(name = "timestamp", nullable = false)
    val timestamp: Instant,

    @Column(name = "consumer_id", length = 36)
    val consumerId: String? = null,

    @Column(name = "worker_id", length = 36)
    val workerId: String? = null,

    @Column(name = "amount", precision = 12, scale = 2)
    val amount: BigDecimal? = null,

    @Column(name = "service_type", length = 30)
    val serviceType: String? = null,

    @Column(name = "payment_channel", length = 50)
    val paymentChannel: String? = null,

    @Column(name = "worker_payout", precision = 12, scale = 2)
    val workerPayout: BigDecimal? = null,

    @Column(name = "platform_fee", precision = 12, scale = 2)
    val platformFee: BigDecimal? = null,

    @Column(name = "commons_fund", precision = 12, scale = 2)
    val commonsFund: BigDecimal? = null,

    @Column(name = "refund_amount", precision = 12, scale = 2)
    val refundAmount: BigDecimal? = null,

    @Column(name = "refund_reason", columnDefinition = "TEXT")
    val refundReason: String? = null,
)

/** Entity → 领域事件映射 */
fun LedgerEventEntity.toDomain(): LedgerEvent = when (eventType) {
    "CHARGE_CREATED" -> LedgerEvent.ChargeCreated(
        eventId = eventId,
        transactionId = TransactionId(transactionId),
        timestamp = timestamp,
        consumerId = consumerId!!,
        workerId = workerId!!,
        amount = amount!!,
        serviceType = serviceType!!,
        paymentChannel = paymentChannel!!,
    )
    "SETTLEMENT_COMPLETED" -> LedgerEvent.SettlementCompleted(
        eventId = eventId,
        transactionId = TransactionId(transactionId),
        timestamp = timestamp,
        workerPayout = workerPayout!!,
        platformFee = platformFee!!,
        commonsFund = commonsFund!!,
    )
    "REFUND_ISSUED" -> LedgerEvent.RefundIssued(
        eventId = eventId,
        transactionId = TransactionId(transactionId),
        timestamp = timestamp,
        refundAmount = refundAmount!!,
        reason = refundReason!!,
    )
    else -> throw IllegalStateException("未知事件类型: $eventType")
}

/** 领域事件 → Entity */
fun LedgerEvent.toEntity(): LedgerEventEntity = when (this) {
    is LedgerEvent.ChargeCreated -> LedgerEventEntity(
        eventId = eventId,
        eventType = "CHARGE_CREATED",
        transactionId = transactionId.value,
        timestamp = timestamp,
        consumerId = consumerId,
        workerId = workerId,
        amount = amount,
        serviceType = serviceType,
        paymentChannel = paymentChannel,
    )
    is LedgerEvent.SettlementCompleted -> LedgerEventEntity(
        eventId = eventId,
        eventType = "SETTLEMENT_COMPLETED",
        transactionId = transactionId.value,
        timestamp = timestamp,
        workerPayout = workerPayout,
        platformFee = platformFee,
        commonsFund = commonsFund,
    )
    is LedgerEvent.RefundIssued -> LedgerEventEntity(
        eventId = eventId,
        eventType = "REFUND_ISSUED",
        transactionId = transactionId.value,
        timestamp = timestamp,
        refundAmount = refundAmount,
        refundReason = reason,
    )
}
