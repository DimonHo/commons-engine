-- =====================================================
-- V7: 治理模块——提案 + 投票持久化
-- =====================================================

-- 提案表
CREATE TABLE IF NOT EXISTS proposals (
    id                      BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    proposal_id             VARCHAR(36)  NOT NULL,
    title                   VARCHAR(500) NOT NULL,
    description             TEXT         NOT NULL,
    proposed_by             VARCHAR(36)  NOT NULL,
    type                    VARCHAR(30)  NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL,
    discussion_deadline     TIMESTAMPTZ  NOT NULL
);

-- 提案 ID 唯一
CREATE UNIQUE INDEX IF NOT EXISTS idx_proposal_id ON proposals(proposal_id);

-- 按状态查询（看板视图）
CREATE INDEX IF NOT EXISTS idx_proposal_status ON proposals(status);

-- 按提交者查询
CREATE INDEX IF NOT EXISTS idx_proposal_by ON proposals(proposed_by);

-- 投票表
CREATE TABLE IF NOT EXISTS votes (
    id                      BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    proposal_id             VARCHAR(36)  NOT NULL,
    voter_id                VARCHAR(36)  NOT NULL,
    stakeholder_type        VARCHAR(20)  NOT NULL,
    choice                  VARCHAR(10)  NOT NULL,
    voted_at                TIMESTAMPTZ  NOT NULL
);

-- 按提案查询投票
CREATE INDEX IF NOT EXISTS idx_vote_proposal ON votes(proposal_id);

-- 按投票者查询
CREATE INDEX IF NOT EXISTS idx_vote_voter ON votes(voter_id);

-- 一人一票约束（proposal_id + voter_id 唯一）
CREATE UNIQUE INDEX IF NOT EXISTS idx_vote_unique ON votes(proposal_id, voter_id);
