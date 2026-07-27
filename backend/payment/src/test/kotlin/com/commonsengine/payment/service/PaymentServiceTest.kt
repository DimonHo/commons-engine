package com.commonsengine.payment.service

import com.commonsengine.payment.domain.SettlementRule
import com.commonsengine.payment.domain.Transaction
import com.commonsengine.payment.domain.TransactionId
import com.commonsengine.payment.domain.TransactionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentServiceTest {

    @Autowired
    private lateinit var service: PaymentService

    private fun tx(amount: BigDecimal = BigDecimal("100.00")) = Transaction(
        id = TransactionId.random(),
        consumerId = "consumer-1",
        workerId = "worker-1",
        amount = amount,
        serviceType = "RIDE_HAILING",
    )

    @Test
    fun `charge changes status to CHARGED`() {
        val charged = service.charge(tx())
        assertEquals(TransactionStatus.CHARGED, charged.status)
    }

    @Test
    fun `settle splits amount according to rule`() {
        val charged = service.charge(tx(BigDecimal("100.00")))
        val result = service.settle(charged)

        assertEquals(BigDecimal("80.00"), result.workerPayout)
        assertEquals(BigDecimal("15.00"), result.platformFee)
        assertEquals(BigDecimal("5.00"), result.commonsFund)
        assertTrue(result.breakdown.contains("劳动者所得"))
    }

    @Test
    fun `settle rejects non-charged transaction`() {
        val pending = tx() // PENDING status
        assertThrows<IllegalArgumentException> { service.settle(pending) }
    }

    @Test
    fun `settlement rule enforces worker minimum 70 percent`() {
        assertThrows<IllegalArgumentException> {
            SettlementRule(
                workerShareRate = BigDecimal("0.50"),  // 违反底线
                platformOperationRate = BigDecimal("0.40"),
                commonsFundRate = BigDecimal("0.10"),
            )
        }
    }

    @Test
    fun `ledger records charge and settlement events`() {
        val charged = service.charge(tx())
        service.settle(charged)

        val history = service.getTransactionHistory(charged.id)
        assertEquals(2, history.size) // ChargeCreated + SettlementCompleted
    }

    @Test
    fun `custom settlement rule works`() {
        val rule = SettlementRule(
            workerShareRate = BigDecimal("0.90"),
            platformOperationRate = BigDecimal("0.08"),
            commonsFundRate = BigDecimal("0.02"),
        )
        val charged = service.charge(tx(BigDecimal("100.00")))
        val result = service.settle(charged, rule)

        assertEquals(BigDecimal("90.00"), result.workerPayout)
        assertEquals(BigDecimal("8.00"), result.platformFee)
        assertEquals(BigDecimal("2.00"), result.commonsFund)
    }

    @Test
    fun `settlement rule must sum to 100 percent`() {
        assertThrows<IllegalArgumentException> {
            SettlementRule(
                workerShareRate = BigDecimal("0.80"),
                platformOperationRate = BigDecimal("0.15"),
                commonsFundRate = BigDecimal("0.10"), // 总和 = 105%
            )
        }
    }

    @Test
    fun `refund records event in ledger`() {
        val charged = service.charge(tx())
        val refunded = service.refund(charged, "服务取消")

        assertTrue(refunded)
        val history = service.getTransactionHistory(charged.id)
        assertEquals(2, history.size) // ChargeCreated + RefundIssued
    }
}
