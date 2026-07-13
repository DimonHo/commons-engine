package com.commonsengine.payment.api

import com.commonsengine.payment.domain.SettlementRule
import com.commonsengine.payment.domain.Transaction
import com.commonsengine.payment.domain.TransactionId
import com.commonsengine.payment.domain.TransactionStatus
import com.commonsengine.payment.service.PaymentService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

/**
 * 支付分账 REST API
 *
 * 核心流程：发起收款 → 分账结算 → 退款 → 查询账本流水
 * 所有资金流向透明可审计（章程第 4.3 条）。
 *
 * 注意：Transaction 对象不单独持久化（仅事件溯源），因此 settle/refund
 * 端点需要从 charge 响应中回传交易信息。
 */
@RestController
@RequestMapping("/api/v1/payment")
open class PaymentController(
    private val service: PaymentService,
) {

    /** 发起收款——创建交易并执行收款，返回已收款交易 */
    @PostMapping("/charge")
    fun charge(@RequestBody body: ChargeRequest): TransactionResponse {
        val tx = Transaction(
            id = TransactionId.random(),
            consumerId = body.consumerId,
            workerId = body.workerId,
            amount = body.amount,
            serviceType = body.serviceType,
        )
        val charged = service.charge(tx)
        return charged.toResponse()
    }

    /**
     * 执行分账——传入 charge 返回的交易信息
     *
     * @param transactionId 从 charge 响应获取
     */
    @PostMapping("/{transactionId}/settle")
    fun settle(
        @PathVariable transactionId: String,
        @RequestBody body: SettleRequest,
    ): SettlementResponse {
        val rule = if (body.workerRate != null && body.operationRate != null && body.commonsRate != null) {
            SettlementRule(
                workerShareRate = BigDecimal(body.workerRate.toString()),
                platformOperationRate = BigDecimal(body.operationRate.toString()),
                commonsFundRate = BigDecimal(body.commonsRate.toString()),
            )
        } else {
            SettlementRule.DEFAULT
        }

        // 从请求体重建已收款交易——Transaction 不持久化，需调用方回传
        val tx = Transaction(
            id = TransactionId(transactionId),
            consumerId = body.consumerId,
            workerId = body.workerId,
            amount = body.amount,
            serviceType = body.serviceType ?: "",
            status = TransactionStatus.CHARGED,
        )

        val result = service.settle(tx, rule)
        return SettlementResponse(
            transactionId = result.transactionId.value,
            totalAmount = result.totalAmount.toString(),
            workerPayout = result.workerPayout.toString(),
            platformFee = result.platformFee.toString(),
            commonsFund = result.commonsFund.toString(),
            workerShareRate = result.rule.workerShareRate.toString(),
            breakdown = result.breakdown,
        )
    }

    /** 退款 */
    @PostMapping("/{transactionId}/refund")
    fun refund(
        @PathVariable transactionId: String,
        @RequestBody body: RefundRequest,
    ): Map<String, Any> {
        val tx = Transaction(
            id = TransactionId(transactionId),
            consumerId = body.consumerId,
            workerId = body.workerId,
            amount = body.amount,
            serviceType = body.serviceType ?: "",
        )
        val success = service.refund(tx, body.reason)
        return mapOf("success" to success, "transactionId" to transactionId)
    }

    /** 查询交易流水（公开审计——章程第 4.3 条） */
    @GetMapping("/{transactionId}/history")
    fun getHistory(@PathVariable transactionId: String): List<Map<String, Any?>> {
        val events = service.getTransactionHistory(TransactionId(transactionId))
        return events.map { event ->
            when (event) {
                is com.commonsengine.payment.domain.LedgerEvent.ChargeCreated -> mapOf(
                    "type" to "CHARGE_CREATED",
                    "eventId" to event.eventId,
                    "transactionId" to event.transactionId.value,
                    "timestamp" to event.timestamp.toString(),
                    "consumerId" to event.consumerId,
                    "amount" to event.amount.toString(),
                    "paymentChannel" to event.paymentChannel,
                )
                is com.commonsengine.payment.domain.LedgerEvent.SettlementCompleted -> mapOf(
                    "type" to "SETTLEMENT_COMPLETED",
                    "eventId" to event.eventId,
                    "transactionId" to event.transactionId.value,
                    "timestamp" to event.timestamp.toString(),
                    "workerPayout" to event.workerPayout.toString(),
                    "platformFee" to event.platformFee.toString(),
                    "commonsFund" to event.commonsFund.toString(),
                )
                is com.commonsengine.payment.domain.LedgerEvent.RefundIssued -> mapOf(
                    "type" to "REFUND_ISSUED",
                    "eventId" to event.eventId,
                    "transactionId" to event.transactionId.value,
                    "timestamp" to event.timestamp.toString(),
                    "refundAmount" to event.refundAmount.toString(),
                    "reason" to event.reason,
                )
            }
        }
    }
}

// ── DTO ──────────────────────────────────────────────────────────

data class ChargeRequest(
    val consumerId: String,
    val workerId: String,
    val amount: BigDecimal,
    val serviceType: String,
)

data class SettleRequest(
    val consumerId: String,
    val workerId: String,
    val amount: BigDecimal,
    val serviceType: String? = null,
    val workerRate: Double? = null,
    val operationRate: Double? = null,
    val commonsRate: Double? = null,
)

data class RefundRequest(
    val consumerId: String,
    val workerId: String,
    val amount: BigDecimal,
    val serviceType: String? = null,
    val reason: String,
)

data class TransactionResponse(
    val id: String,
    val consumerId: String,
    val workerId: String,
    val amount: String,
    val serviceType: String,
    val status: String,
)

data class SettlementResponse(
    val transactionId: String,
    val totalAmount: String,
    val workerPayout: String,
    val platformFee: String,
    val commonsFund: String,
    val workerShareRate: String,
    val breakdown: String,
)

private fun Transaction.toResponse() = TransactionResponse(
    id = id.value,
    consumerId = consumerId,
    workerId = workerId,
    amount = amount.toString(),
    serviceType = serviceType,
    status = status.name,
)
