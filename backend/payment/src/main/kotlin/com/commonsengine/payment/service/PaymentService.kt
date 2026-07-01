package com.commonsengine.payment.service

import com.commonsengine.payment.domain.LedgerEvent
import com.commonsengine.payment.domain.SettlementResult
import com.commonsengine.payment.domain.SettlementRule
import com.commonsengine.payment.domain.Transaction
import com.commonsengine.payment.domain.TransactionId
import com.commonsengine.payment.domain.TransactionStatus
import com.commonsengine.payment.gateway.MockPaymentGateway
import com.commonsengine.payment.gateway.PaymentGateway
import com.commonsengine.payment.ledger.LedgerBook
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/**
 * 支付分账服务
 *
 * 核心流程：收款 → 按规则分账 → 记录账本 → 向劳动者打款
 * 所有步骤透明可审计。
 */
@Service
open class PaymentService(
    private val gateway: PaymentGateway = MockPaymentGateway(),
    private val ledger: LedgerBook = LedgerBook(),
) {

    /**
     * 发起收款
     */
    fun charge(transaction: Transaction): Transaction {
        val externalId = gateway.charge(transaction)
        val charged = transaction.copy(status = TransactionStatus.CHARGED)

        ledger.append(
            LedgerEvent.ChargeCreated(
                eventId = UUID.randomUUID().toString(),
                transactionId = charged.id,
                timestamp = Instant.now(),
                consumerId = charged.consumerId,
                amount = charged.amount,
                paymentChannel = gateway.channelName,
            )
        )

        return charged
    }

    /**
     * 执行分账——按 SettlementRule 分配资金
     */
    fun settle(transaction: Transaction, rule: SettlementRule = SettlementRule.DEFAULT): SettlementResult {
        require(transaction.status == TransactionStatus.CHARGED) {
            "交易必须为 CHARGED 状态才能分账，当前: ${transaction.status}"
        }

        val workerPayout = transaction.amount
            .multiply(rule.workerShareRate)
            .setScale(2, RoundingMode.HALF_UP)
        val platformFee = transaction.amount
            .multiply(rule.platformOperationRate)
            .setScale(2, RoundingMode.HALF_UP)
        val commonsFund = transaction.amount
            .multiply(rule.commonsFundRate)
            .setScale(2, RoundingMode.HALF_UP)

        // 向劳动者打款
        val payoutSuccess = gateway.payout(transaction.workerId, workerPayout)
        if (!payoutSuccess) {
            throw RuntimeException("向劳动者 ${transaction.workerId} 打款失败")
        }

        // 记录分账事件
        ledger.append(
            LedgerEvent.SettlementCompleted(
                eventId = UUID.randomUUID().toString(),
                transactionId = transaction.id,
                timestamp = Instant.now(),
                workerPayout = workerPayout,
                platformFee = platformFee,
                commonsFund = commonsFund,
            )
        )

        val breakdown = buildString {
            append("费用拆分：\n")
            append("  总金额：¥${transaction.amount}\n")
            append("  劳动者所得（${(rule.workerShareRate * BigDecimal(100)).toInt()}%）：¥$workerPayout\n")
            append("  平台运营（${(rule.platformOperationRate * BigDecimal(100)).toInt()}%）：¥$platformFee\n")
            append("  公积金（${(rule.commonsFundRate * BigDecimal(100)).toInt()}%）：¥$commonsFund")
        }

        return SettlementResult(
            transactionId = transaction.id,
            totalAmount = transaction.amount,
            workerPayout = workerPayout,
            platformFee = platformFee,
            commonsFund = commonsFund,
            rule = rule,
            breakdown = breakdown,
        )
    }

    /**
     * 退款
     */
    fun refund(transaction: Transaction, reason: String): Boolean {
        val success = gateway.refund(transaction, reason)
        if (success) {
            ledger.append(
                LedgerEvent.RefundIssued(
                    eventId = UUID.randomUUID().toString(),
                    transactionId = transaction.id,
                    timestamp = Instant.now(),
                    refundAmount = transaction.amount,
                    reason = reason,
                )
            )
        }
        return success
    }

    /**
     * 查询交易流水
     */
    fun getTransactionHistory(txId: TransactionId): List<LedgerEvent> = ledger.findByTransaction(txId)
}
