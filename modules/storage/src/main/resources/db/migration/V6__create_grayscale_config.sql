-- V6__create_grayscale_config.sql
-- 创建灰度发布配置表和灰度指标表

-- 灰度发布配置表
CREATE TABLE IF NOT EXISTS grayscale_configs (
    id BIGSERIAL PRIMARY KEY,
    rule_key VARCHAR(255) NOT NULL,
    current_version INT NOT NULL,
    grayscale_version INT NOT NULL,
    grayscale_percentage INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 灰度指标表
CREATE TABLE IF NOT EXISTS grayscale_metrics (
    id BIGSERIAL PRIMARY KEY,
    grayscale_config_id BIGINT NOT NULL REFERENCES grayscale_configs(id),
    version INT NOT NULL,
    execution_count INT DEFAULT 0,
    hit_count INT DEFAULT 0,
    error_count INT DEFAULT 0,
    avg_execution_time_ms INT DEFAULT 0,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_grayscale_configs_rule_key ON grayscale_configs(rule_key);
CREATE INDEX idx_grayscale_configs_status ON grayscale_configs(status);
CREATE INDEX idx_grayscale_configs_rule_key_status ON grayscale_configs(rule_key, status);
CREATE INDEX idx_grayscale_metrics_config_id ON grayscale_metrics(grayscale_config_id);
CREATE INDEX idx_grayscale_metrics_config_id_version ON grayscale_metrics(grayscale_config_id, version);

-- 添加注释
COMMENT ON TABLE grayscale_configs IS '灰度发布配置表';
COMMENT ON COLUMN grayscale_configs.rule_key IS '规则Key';
COMMENT ON COLUMN grayscale_configs.current_version IS '当前版本号';
COMMENT ON COLUMN grayscale_configs.grayscale_version IS '灰度版本号';
COMMENT ON COLUMN grayscale_configs.grayscale_percentage IS '灰度流量百分比(0-100)';
COMMENT ON COLUMN grayscale_configs.status IS '灰度状态: DRAFT/RUNNING/COMPLETED/ROLLED_BACK';
COMMENT ON COLUMN grayscale_configs.started_at IS '灰度开始时间';
COMMENT ON COLUMN grayscale_configs.completed_at IS '灰度完成时间';
COMMENT ON COLUMN grayscale_configs.created_by IS '创建人';
COMMENT ON COLUMN grayscale_configs.created_at IS '创建时间';

COMMENT ON TABLE grayscale_metrics IS '灰度指标表';
COMMENT ON COLUMN grayscale_metrics.grayscale_config_id IS '灰度配置ID';
COMMENT ON COLUMN grayscale_metrics.version IS '版本号(当前版本或灰度版本)';
COMMENT ON COLUMN grayscale_metrics.execution_count IS '执行次数';
COMMENT ON COLUMN grayscale_metrics.hit_count IS '命中次数';
COMMENT ON COLUMN grayscale_metrics.error_count IS '错误次数';
COMMENT ON COLUMN grayscale_metrics.avg_execution_time_ms IS '平均执行时间(毫秒)';
COMMENT ON COLUMN grayscale_metrics.recorded_at IS '记录时间';
