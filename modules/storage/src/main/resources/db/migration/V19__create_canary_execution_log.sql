-- 灰度执行日志表
-- 记录灰度分流的每次执行详情，用于灰度效果对比和问题排查
CREATE TABLE IF NOT EXISTS canary_execution_log (
    id              BIGSERIAL PRIMARY KEY,
    trace_id        VARCHAR(64) NOT NULL,
    target_type     VARCHAR(20) NOT NULL DEFAULT 'RULE',
    target_key      VARCHAR(255) NOT NULL,
    version_used    INT NOT NULL,
    is_canary       BOOLEAN NOT NULL DEFAULT FALSE,
    request_features JSONB,
    decision_result VARCHAR(50),
    execution_time_ms BIGINT,
    error_message   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 索引：按 trace_id 查询
CREATE INDEX IF NOT EXISTS idx_canary_log_trace_id ON canary_execution_log(trace_id);

-- 索引：按目标类型+目标Key+时间范围查询（灰度效果对比）
CREATE INDEX IF NOT EXISTS idx_canary_log_target_time ON canary_execution_log(target_type, target_key, created_at);

-- 索引：按是否灰度查询（快速统计灰度命中数）
CREATE INDEX IF NOT EXISTS idx_canary_log_is_canary ON canary_execution_log(is_canary);
