-- =====================================================
-- V4: 评价记录——双向评价 + 标签 + 信用画像聚合源
-- =====================================================

CREATE TABLE IF NOT EXISTS ratings (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rating_id       VARCHAR(36)  NOT NULL,
    transaction_id  VARCHAR(36)  NOT NULL,
    rater_id        VARCHAR(36)  NOT NULL,
    ratee_id        VARCHAR(36)  NOT NULL,
    direction       VARCHAR(30)  NOT NULL,
    score           INT          NOT NULL CHECK (score >= 1 AND score <= 5),
    tags            VARCHAR(255),
    comment         TEXT,
    created_at      TIMESTAMPTZ  NOT NULL
);

-- 评价 ID 唯一
CREATE UNIQUE INDEX IF NOT EXISTS idx_rating_id ON ratings(rating_id);

-- 按被评价者查询（信用画像聚合）
CREATE INDEX IF NOT EXISTS idx_rating_ratee ON ratings(ratee_id);

-- 按交易查询
CREATE INDEX IF NOT EXISTS idx_rating_tx ON ratings(transaction_id);

-- 按评价者查询
CREATE INDEX IF NOT EXISTS idx_rating_rater ON ratings(rater_id);
