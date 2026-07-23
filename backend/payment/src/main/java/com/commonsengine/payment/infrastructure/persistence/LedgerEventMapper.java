package com.commonsengine.payment.infrastructure.persistence;

import com.commonsengine.payment.domain.Model.LedgerEvent;
import com.commonsengine.payment.domain.Model.TransactionId;

/**
 * LedgerEvent entity ↔ domain 映射器。
 *
 * <p>Kotlin 版使用扩展函数 {@code LedgerEventEntity.toDomain()} / {@code LedgerEvent.toEntity()}，
 * Java 版等价为 {@code LedgerEventMapper} 的静态方法。
 */
public final class LedgerEventMapper {

    static final String TYPE_CHARGE_CREATED = "CHARGE_CREATED";
    static final String TYPE_SETTLEMENT_COMPLETED = "SETTLEMENT_COMPLETED";
    static final String TYPE_REFUND_ISSUED = "REFUND_ISSUED";

    private LedgerEventMapper() {
    }

    /** Entity → 领域事件 */
    public static LedgerEvent toDomain(LedgerEventEntity e) {
        String type = e.getEventType();
        return switch (type) {
            case TYPE_CHARGE_CREATED -> new LedgerEvent.ChargeCreated(
                    e.getEventId(),
                    new TransactionId(e.getTransactionId()),
                    e.getTimestamp(),
                    e.getConsumerId(),
                    e.getWorkerId(),
                    e.getAmount(),
                    e.getServiceType(),
                    e.getPaymentChannel()
            );
            case TYPE_SETTLEMENT_COMPLETED -> new LedgerEvent.SettlementCompleted(
                    e.getEventId(),
                    new TransactionId(e.getTransactionId()),
                    e.getTimestamp(),
                    e.getWorkerPayout(),
                    e.getPlatformFee(),
                    e.getCommonsFund()
            );
            case TYPE_REFUND_ISSUED -> new LedgerEvent.RefundIssued(
                    e.getEventId(),
                    new TransactionId(e.getTransactionId()),
                    e.getTimestamp(),
                    e.getRefundAmount(),
                    e.getRefundReason()
            );
            default -> throw new IllegalStateException("未知事件类型: " + type);
        };
    }

    /** 领域事件 → Entity（新建） */
    public static LedgerEventEntity toEntity(LedgerEvent event) {
        LedgerEventEntity e = new LedgerEventEntity();
        e.setEventId(event.eventId());
        e.setTransactionId(event.transactionId().value());
        e.setTimestamp(event.timestamp());

        if (event instanceof LedgerEvent.ChargeCreated c) {
            e.setEventType(TYPE_CHARGE_CREATED);
            e.setConsumerId(c.consumerId());
            e.setWorkerId(c.workerId());
            e.setAmount(c.amount());
            e.setServiceType(c.serviceType());
            e.setPaymentChannel(c.paymentChannel());
        } else if (event instanceof LedgerEvent.SettlementCompleted s) {
            e.setEventType(TYPE_SETTLEMENT_COMPLETED);
            e.setWorkerPayout(s.workerPayout());
            e.setPlatformFee(s.platformFee());
            e.setCommonsFund(s.commonsFund());
        } else if (event instanceof LedgerEvent.RefundIssued r) {
            e.setEventType(TYPE_REFUND_ISSUED);
            e.setRefundAmount(r.refundAmount());
            e.setRefundReason(r.reason());
        } else {
            throw new IllegalStateException("未知事件类型: " + event.getClass().getName());
        }
        return e;
    }
}
