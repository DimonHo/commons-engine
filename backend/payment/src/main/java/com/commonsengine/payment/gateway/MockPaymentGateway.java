package com.commonsengine.payment.gateway;

import com.commonsengine.payment.domain.Model.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 模拟支付通道——MVP 阶段用于开发测试。
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final String CHANNEL_NAME = "mock-channel";

    @Override
    public String getChannelName() {
        return CHANNEL_NAME;
    }

    @Override
    public String charge(Transaction transaction) {
        // 模拟支付成功，返回流水号（取交易 ID 前 8 位）
        String id = transaction.id().value();
        String prefix = id.length() >= 8 ? id.substring(0, 8) : id;
        return "MOCK-" + prefix;
    }

    @Override
    public boolean payout(String workerId, BigDecimal amount) {
        // 模拟打款成功
        return true;
    }

    @Override
    public boolean refund(Transaction transaction, String reason) {
        return true;
    }
}
