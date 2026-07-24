package com.commonsengine.payment.gateway;

import com.commonsengine.payment.domain.Model.Transaction;

import java.math.BigDecimal;

/**
 * 支付网关适配器接口——适配器模式，支持多支付通道。
 *
 * <p>公地引擎通过适配器模式接入不同地区的支付基础设施：
 * 微信支付、支付宝、银行转账等。
 *
 * <p>实际调用时通过 HTTP/SDK 与支付通道通信。
 * MVP 阶段使用 {@link MockPaymentGateway} 模拟实现。
 */
public interface PaymentGateway {

    /**
     * 支付通道名称（如 "wechat-pay", "alipay", "bank-transfer"）
     */
    String getChannelName();

    /**
     * 向消费者发起收款。
     *
     * @param transaction 交易
     * @return 支付通道返回的交易流水号
     */
    String charge(Transaction transaction);

    /**
     * 向劳动者打款（结算）。
     *
     * @param workerId 劳动者 ID
     * @param amount   打款金额
     * @return 是否打款成功
     */
    boolean payout(String workerId, BigDecimal amount);

    /**
     * 退款。
     *
     * @param transaction 原交易
     * @param reason      退款原因
     * @return 是否退款成功
     */
    boolean refund(Transaction transaction, String reason);
}
