package com.commonsengine.payment.service;

import com.commonsengine.payment.domain.Model.LedgerEvent;
import com.commonsengine.payment.domain.Model.SettlementResult;
import com.commonsengine.payment.domain.Model.SettlementRule;
import com.commonsengine.payment.domain.Model.Transaction;
import com.commonsengine.payment.domain.Model.TransactionId;
import com.commonsengine.payment.domain.Model.TransactionStatus;
import com.commonsengine.payment.gateway.PaymentGateway;
import com.commonsengine.payment.ledger.LedgerBook;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 支付分账服务。
 *
 * <p>核心流程：收款 → 按规则分账 → 记录账本 → 向劳动者打款。
 * 所有步骤透明可审计。
 */
@Service
public class PaymentService {

    private final PaymentGateway gateway;
    private final LedgerBook ledger;

    public PaymentService(PaymentGateway gateway, LedgerBook ledger) {
        this.gateway = gateway;
        this.ledger = ledger;
    }

    /**
     * 发起收款。
     */
    public Transaction charge(Transaction transaction) {
        gateway.charge(transaction);
        Transaction charged = transaction.withStatus(TransactionStatus.CHARGED);

        ledger.append(new LedgerEvent.ChargeCreated(
                UUID.randomUUID().toString(),
                charged.id(),
                Instant.now(),
                charged.consumerId(),
                charged.workerId(),
                charged.amount(),
                charged.serviceType(),
                gateway.getChannelName()
        ));

        return charged;
    }

    /**
     * 从事件存储重建交易——settle/refund 加载权威记录。
     *
     * <p>通过 CHARGE_CREATED 事件恢复交易的完整字段（消费者、劳动者、金额、服务类型），
     * 防止调用方伪造交易信息。
     *
     * @return 已收款交易，或 empty（交易不存在）
     */
    public Optional<Transaction> findById(TransactionId txId) {
        List<LedgerEvent> events = ledger.findByTransaction(txId);
        LedgerEvent.ChargeCreated chargeEvent = events.stream()
                .filter(e -> e instanceof LedgerEvent.ChargeCreated)
                .map(e -> (LedgerEvent.ChargeCreated) e)
                .findFirst()
                .orElse(null);
        if (chargeEvent == null) {
            return Optional.empty();
        }

        boolean isSettled = events.stream().anyMatch(e -> e instanceof LedgerEvent.SettlementCompleted);
        boolean isRefunded = events.stream().anyMatch(e -> e instanceof LedgerEvent.RefundIssued);
        TransactionStatus status;
        if (isRefunded) {
            status = TransactionStatus.REFUNDED;
        } else if (isSettled) {
            status = TransactionStatus.SETTLED;
        } else {
            status = TransactionStatus.CHARGED;
        }

        return Optional.of(new Transaction(
                chargeEvent.transactionId(),
                chargeEvent.consumerId(),
                chargeEvent.workerId(),
                chargeEvent.amount(),
                chargeEvent.serviceType(),
                chargeEvent.timestamp(),
                status
        ));
    }

    /**
     * 执行分账——按 {@link SettlementRule} 分配资金。
     */
    public SettlementResult settle(Transaction transaction, SettlementRule rule) {
        if (transaction.status() != TransactionStatus.CHARGED) {
            throw new IllegalArgumentException(
                    "交易必须为 CHARGED 状态才能分账，当前: " + transaction.status());
        }

        BigDecimal workerPayout = transaction.amount()
                .multiply(rule.workerShareRate())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal platformFee = transaction.amount()
                .multiply(rule.platformOperationRate())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal commonsFund = transaction.amount()
                .multiply(rule.commonsFundRate())
                .setScale(2, RoundingMode.HALF_UP);

        // 向劳动者打款
        boolean payoutSuccess = gateway.payout(transaction.workerId(), workerPayout);
        if (!payoutSuccess) {
            throw new RuntimeException("向劳动者 " + transaction.workerId() + " 打款失败");
        }

        // 记录分账事件
        ledger.append(new LedgerEvent.SettlementCompleted(
                UUID.randomUUID().toString(),
                transaction.id(),
                Instant.now(),
                workerPayout,
                platformFee,
                commonsFund
        ));

        String breakdown = "费用拆分：\n"
                + "  总金额：¥" + transaction.amount() + "\n"
                + "  劳动者所得（" + rule.workerShareRate().multiply(BigDecimal.valueOf(100)).intValue() + "%）：¥" + workerPayout + "\n"
                + "  平台运营（" + rule.platformOperationRate().multiply(BigDecimal.valueOf(100)).intValue() + "%）：¥" + platformFee + "\n"
                + "  公积金（" + rule.commonsFundRate().multiply(BigDecimal.valueOf(100)).intValue() + "%）：¥" + commonsFund;

        return new SettlementResult(
                transaction.id(),
                transaction.amount(),
                workerPayout,
                platformFee,
                commonsFund,
                rule,
                breakdown
        );
    }

    /** 使用默认分账规则执行分账 */
    public SettlementResult settle(Transaction transaction) {
        return settle(transaction, SettlementRule.DEFAULT);
    }

    /**
     * 退款。
     */
    public boolean refund(Transaction transaction, String reason) {
        boolean success = gateway.refund(transaction, reason);
        if (success) {
            ledger.append(new LedgerEvent.RefundIssued(
                    UUID.randomUUID().toString(),
                    transaction.id(),
                    Instant.now(),
                    transaction.amount(),
                    reason
            ));
        }
        return success;
    }

    /**
     * 查询交易流水。
     */
    public List<LedgerEvent> getTransactionHistory(TransactionId txId) {
        return ledger.findByTransaction(txId);
    }
}
