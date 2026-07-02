-- =====================================================
-- V2: 匹配引擎——劳动者实时位置表
-- =====================================================

CREATE TABLE IF NOT EXISTS worker_locations (
    worker_id           VARCHAR(36)   PRIMARY KEY,
    name                VARCHAR(100)  NOT NULL,
    lat                 DOUBLE PRECISION NOT NULL,
    lng                 DOUBLE PRECISION NOT NULL,
    service_types       VARCHAR(200)  NOT NULL DEFAULT '',
    rating              DOUBLE PRECISION NOT NULL DEFAULT 5.0,
    active_order_count  INT           NOT NULL DEFAULT 0,
    last_seen_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- 经纬度复合索引（bounding box 查询用）
CREATE INDEX IF NOT EXISTS idx_worker_locations_lat_lng
    ON worker_locations(lat, lng);

-- 服务类型索引
CREATE INDEX IF NOT EXISTS idx_worker_locations_service_types
    ON worker_locations(service_types);

-- 活跃度索引（过滤满单劳动者）
CREATE INDEX IF NOT EXISTS idx_worker_locations_active
    ON worker_locations(active_order_count);
