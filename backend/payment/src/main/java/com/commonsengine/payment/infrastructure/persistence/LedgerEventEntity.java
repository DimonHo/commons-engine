package com.commonsengine.payment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 账本事件 JPA 实体——不可篡改的资金流水。
 *
 * <p>单表设计，用 event_type 区分三种事件（ChargeCreated/SettlementCompleted/RefundIssued）。
 * 对应领域模型 {@code LedgerEvent} 密封接口的三个 record 子类。
 *
 * <p>Append-only：只插入，不修改不删除。
 *
 * <p>JPA 要求无参构造器与可变字段，故使用普通 Java 类。
 */
@Entity
@Table(name = "ledger_events")
public class LedgerEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "transaction_id", nullable = false, length = 36)
    private String transactionId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "consumer_id", length = 36)
    private String consumerId;

    @Column(name = "worker_id", length = 36)
    private String workerId;

    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "service_type", length = 30)
    private String serviceType;

    @Column(name = "payment_channel", length = 50)
    private String paymentChannel;

    @Column(name = "worker_payout", precision = 12, scale = 2)
    private BigDecimal workerPayout;

    @Column(name = "platform_fee", precision = 12, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "commons_fund", precision = 12, scale = 2)
    private BigDecimal commonsFund;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_reason", columnDefinition = "TEXT")
    private String refundReason;

    /** JPA 要求的无参构造器 */
    public LedgerEventEntity() {
    }

    // ── getters / setters ──────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getPaymentChannel() {
        return paymentChannel;
    }

    public void setPaymentChannel(String paymentChannel) {
        this.paymentChannel = paymentChannel;
    }

    public BigDecimal getWorkerPayout() {
        return workerPayout;
    }

    public void setWorkerPayout(BigDecimal workerPayout) {
        this.workerPayout = workerPayout;
    }

    public BigDecimal getPlatformFee() {
        return platformFee;
    }

    public void setPlatformFee(BigDecimal platformFee) {
        this.platformFee = platformFee;
    }

    public BigDecimal getCommonsFund() {
        return commonsFund;
    }

    public void setCommonsFund(BigDecimal commonsFund) {
        this.commonsFund = commonsFund;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }
}
