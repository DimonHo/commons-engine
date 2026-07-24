package com.commonsengine.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commonsengine.payment.domain.Model.LedgerEvent;
import com.commonsengine.payment.domain.Model.SettlementResult;
import com.commonsengine.payment.domain.Model.SettlementRule;
import com.commonsengine.payment.domain.Model.Transaction;
import com.commonsengine.payment.domain.Model.TransactionId;
import com.commonsengine.payment.domain.Model.TransactionStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentServiceTest {

    @Autowired
    private PaymentService service;

    private Transaction tx() {
        return tx(new BigDecimal("100.00"));
    }

    private Transaction tx(BigDecimal amount) {
        return new Transaction(
                TransactionId.random(),
                "consumer-1",
                "worker-1",
                amount,
                "RIDE_HAILING",
                null,
                null
        );
    }

    @Test
    void chargeChangesStatusToCharged() {
        Transaction charged = service.charge(tx());
        assertEquals(TransactionStatus.CHARGED, charged.status());
    }

    @Test
    void settleSplitsAmountAccordingToRule() {
        Transaction charged = service.charge(tx(new BigDecimal("100.00")));
        SettlementResult result = service.settle(charged);

        assertEquals(new BigDecimal("80.00"), result.workerPayout());
        assertEquals(new BigDecimal("15.00"), result.platformFee());
        assertEquals(new BigDecimal("5.00"), result.commonsFund());
        assertTrue(result.breakdown().contains("劳动者所得"));
    }

    @Test
    void settleRejectsNonChargedTransaction() {
        Transaction pending = tx(); // PENDING status
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.settle(pending));
    }

    @Test
    void settlementRuleEnforcesWorkerMinimum70Percent() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new SettlementRule(
                        new BigDecimal("0.50"),  // 违反底线
                        new BigDecimal("0.40"),
                        new BigDecimal("0.10")
                ));
    }

    @Test
    void ledgerRecordsChargeAndSettlementEvents() {
        Transaction charged = service.charge(tx());
        service.settle(charged);

        List<LedgerEvent> history = service.getTransactionHistory(charged.id());
        assertEquals(2, history.size()); // ChargeCreated + SettlementCompleted
    }

    @Test
    void customSettlementRuleWorks() {
        SettlementRule rule = new SettlementRule(
                new BigDecimal("0.90"),
                new BigDecimal("0.08"),
                new BigDecimal("0.02")
        );
        Transaction charged = service.charge(tx(new BigDecimal("100.00")));
        SettlementResult result = service.settle(charged, rule);

        assertEquals(new BigDecimal("90.00"), result.workerPayout());
        assertEquals(new BigDecimal("8.00"), result.platformFee());
        assertEquals(new BigDecimal("2.00"), result.commonsFund());
    }

    @Test
    void settlementRuleMustSumTo100Percent() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new SettlementRule(
                        new BigDecimal("0.80"),
                        new BigDecimal("0.15"),
                        new BigDecimal("0.10") // 总和 = 105%
                ));
    }

    @Test
    void refundRecordsEventInLedger() {
        Transaction charged = service.charge(tx());
        boolean refunded = service.refund(charged, "服务取消");

        assertTrue(refunded);
        List<LedgerEvent> history = service.getTransactionHistory(charged.id());
        assertEquals(2, history.size()); // ChargeCreated + RefundIssued
    }
}
