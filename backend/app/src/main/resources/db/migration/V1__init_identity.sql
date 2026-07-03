-- =====================================================
-- 公地引擎 V1 初始化迁移
-- 创建核心表结构：身份/会员系统
-- =====================================================
-- 说明：曾在此处 `CREATE EXTENSION postgis`，但当前无任何迁移/实体使用
-- PostGIS 类型（worker_locations 用 lat/lng 平面列 + bounding-box 检索），
-- 属于未使用的硬依赖，会导致非 PostGIS 的 PostgreSQL 无法启动（破坏 demo）。
-- 待 #44 真正采用 ST_DWithin + geography 时再由新迁移启用 postgis 扩展。
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- -----------------------------------------------------
-- 会员表：合作社成员——平台的真正主人
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS members (
    id              VARCHAR(36)   PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(100)  NOT NULL,
    phone           VARCHAR(20)   NOT NULL,
    roles           VARCHAR(200)  NOT NULL,  -- 逗号分隔：WORKER,CONSUMER,COMMUNITY
    registered_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    labor_shares    INT           NOT NULL DEFAULT 0
);

-- 手机号唯一索引（隐私保护：一人一号）
CREATE UNIQUE INDEX IF NOT EXISTS idx_members_phone ON members(phone);

-- 活跃成员索引
CREATE INDEX IF NOT EXISTS idx_members_status ON members(status);

-- -----------------------------------------------------
-- 劳动者档案表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS worker_profiles (
    member_id           VARCHAR(36)   PRIMARY KEY REFERENCES members(id) ON DELETE CASCADE,
    service_types       VARCHAR(200)  NOT NULL,   -- 逗号分隔
    work_region         VARCHAR(100)  NOT NULL,
    license_number      VARCHAR(100),
    rating              DOUBLE PRECISION NOT NULL DEFAULT 5.0,
    total_completed_orders  INT       NOT NULL DEFAULT 0
);

-- -----------------------------------------------------
-- 身份验证记录表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS identity_verifications (
    id              VARCHAR(36)   PRIMARY KEY DEFAULT uuid_generate_v4(),
    member_id       VARCHAR(36)   NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    type            VARCHAR(30)   NOT NULL,  -- PHONE, ID_CARD, LICENSE, BACKGROUND_CHECK
    status          VARCHAR(20)   NOT NULL,  -- UNVERIFIED, PENDING, VERIFIED, REJECTED
    submitted_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    verified_at     TIMESTAMPTZ,
    notes           TEXT
);

CREATE INDEX IF NOT EXISTS idx_verifications_member ON identity_verifications(member_id);
CREATE INDEX IF NOT EXISTS idx_verifications_status ON identity_verifications(status);
