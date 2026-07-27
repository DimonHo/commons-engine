package com.commonsengine.payment.gateway

import com.commonsengine.payment.domain.Transaction
import org.springframework.stereotype.Component

/**
 * 支付网关适配器接口——适配器模式，支持多支付通道
 *
 * 公地引擎通过适配器模式接入不同地区的支付基础设施：
 * 微信支付、支付宝、银行转账等。
 *
 * 实际调用时通过 HTTP/SDK 与支付通道通信。
 * MVP 阶段使用模拟实现。
 */
interface PaymentGateway {

    /** 支付通道名称（如 "wechat-pay", "alipay", "bank-transfer"） */
    val channelName: String

    /**
     * 向消费者发起收款
     *
     * @return 支付通道返回的交易流水号
     */
    fun charge(transaction: Transaction): String

    /**
     * 向劳动者打款（结算）
     */
    fun payout(workerId: String, amount: java.math.BigDecimal): Boolean

    /**
     * 退款
     */
    fun refund(transaction: Transaction, reason: String): Boolean
}

/**
 * 模拟支付通道——MVP 阶段用于开发测试
 */
@Component
open class MockPaymentGateway : PaymentGateway {

    override val channelName = "mock-channel"

    override fun charge(transaction: Transaction): String {
        // 模拟支付成功，返回流水号
        return "MOCK-${transaction.id.value.take(8)}"
    }

    override fun payout(workerId: String, amount: java.math.BigDecimal): Boolean {
        // 模拟打款成功
        return true
    }

    override fun refund(transaction: Transaction, reason: String): Boolean {
        return true
    }
}
