-- =====================================================
-- V5: 纠纷仲裁工单——AI 初筛 + 人工仲裁状态机
-- =====================================================

CREATE TABLE IF NOT EXISTS disputes (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dispute_id      VARCHAR(36)  NOT NULL,
    transaction_id  VARCHAR(36)  NOT NULL,
    filed_by        VARCHAR(36)  NOT NULL,
    filed_against   VARCHAR(36)  NOT NULL,
    type            VARCHAR(30)  NOT NULL,
    priority        VARCHAR(15)  NOT NULL,
    description     TEXT         NOT NULL,
    evidence_urls   TEXT,
    status          VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    resolution      TEXT,
    resolved_at     TIMESTAMPTZ
);

-- 纠纷 ID 唯一
CREATE UNIQUE INDEX IF NOT EXISTS idx_dispute_id ON disputes(dispute_id);

-- 按状态查询（看板视图）
CREATE INDEX IF NOT EXISTS idx_dispute_status ON disputes(status);

-- 按提交者查询
CREATE INDEX IF NOT EXISTS idx_dispute_filed_by ON disputes(filed_by);

-- 按被投诉者查询
CREATE INDEX IF NOT EXISTS idx_dispute_filed_against ON disputes(filed_against);
