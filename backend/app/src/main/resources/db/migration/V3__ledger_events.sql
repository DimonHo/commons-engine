-- =====================================================
-- V3: 支付/分账事件账本——不可篡改流水
-- =====================================================

CREATE TABLE IF NOT EXISTS ledger_events (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id            VARCHAR(36)  NOT NULL,
    event_type          VARCHAR(30)  NOT NULL,
    transaction_id      VARCHAR(36)  NOT NULL,
    timestamp           TIMESTAMPTZ  NOT NULL,

    -- ChargeCreated 字段
    consumer_id         VARCHAR(36),
    amount              DECIMAL(12,2),
    payment_channel     VARCHAR(50),

    -- SettlementCompleted 字段
    worker_payout       DECIMAL(12,2),
    platform_fee        DECIMAL(12,2),
    commons_fund        DECIMAL(12,2),

    -- RefundIssued 字段
    refund_amount       DECIMAL(12,2),
    refund_reason       TEXT
);

-- 事件 ID 唯一（防重放）
CREATE UNIQUE INDEX IF NOT EXISTS idx_ledger_event_id ON ledger_events(event_id);

-- 按交易查询索引
CREATE INDEX IF NOT EXISTS idx_ledger_tx ON ledger_events(transaction_id);

-- 按事件类型索引（审计查询）
CREATE INDEX IF NOT EXISTS idx_ledger_type ON ledger_events(event_type);

-- 按时间索引（按月审计）
CREATE INDEX IF NOT EXISTS idx_ledger_time ON ledger_events(timestamp);
