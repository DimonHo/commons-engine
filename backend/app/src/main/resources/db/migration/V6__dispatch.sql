-- =====================================================
-- V6: 调度模块——任务持久化 + 劳动者工作偏好
-- =====================================================

-- 调度任务表
CREATE TABLE IF NOT EXISTS dispatch_tasks (
    id                          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id                     VARCHAR(36)  NOT NULL,
    worker_id                   VARCHAR(36)  NOT NULL,
    service_type                VARCHAR(30)  NOT NULL,
    pickups                     TEXT         NOT NULL,      -- JSON: [{"lat":..,"lng":..}]
    dropoffs                    TEXT         NOT NULL,      -- JSON: [{"lat":..,"lng":..}]
    estimated_distance_meters   DOUBLE PRECISION NOT NULL DEFAULT 0,
    estimated_duration_minutes  INT          NOT NULL DEFAULT 0,
    assigned_at                 TIMESTAMPTZ  NOT NULL,
    deadline                    TIMESTAMPTZ
);

-- 任务 ID 唯一
CREATE UNIQUE INDEX IF NOT EXISTS idx_dispatch_task_id ON dispatch_tasks(task_id);

-- 按劳动者查询（今日任务）
CREATE INDEX IF NOT EXISTS idx_dispatch_worker ON dispatch_tasks(worker_id);

-- 按分配时间查询（任务看板）
CREATE INDEX IF NOT EXISTS idx_dispatch_assigned ON dispatch_tasks(assigned_at);

-- 劳动者工作偏好表
CREATE TABLE IF NOT EXISTS worker_preferences (
    id                          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    worker_id                   VARCHAR(36)  NOT NULL,
    preferred_service_types     TEXT,        -- JSON: ["RIDE_HAILING","FOOD_DELIVERY"]
    preferred_regions           TEXT,        -- JSON: ["chengdu_wuhou", ...]
    excluded_regions            TEXT,        -- JSON: ["chengdu_jinjiang", ...]
    preferred_time_slots        TEXT,        -- JSON: [{"dayOfWeek":1,"startHour":8,"endHour":12}, ...]
    excluded_time_slots         TEXT,        -- JSON: [{"dayOfWeek":7,"startHour":0,"endHour":6}, ...]
    max_concurrent_orders       INT          NOT NULL DEFAULT 3,
    max_daily_hours             DOUBLE PRECISION NOT NULL DEFAULT 12.0
);

-- 劳动者 ID 唯一（一人一套偏好）
CREATE UNIQUE INDEX IF NOT EXISTS idx_worker_prefs_worker ON worker_preferences(worker_id);
