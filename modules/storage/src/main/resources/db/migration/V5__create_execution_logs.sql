-- V5__create_execution_logs.sql
-- 创建规则执行日志表

CREATE TABLE IF NOT EXISTS execution_logs (
    id BIGSERIAL PRIMARY KEY,
    rule_key VARCHAR(255) NOT NULL,
    rule_version INT,
    input_features JSONB,
    output_decision VARCHAR(50),
    output_reason TEXT,
    execution_time_ms INT,
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引以提升查询性能
CREATE INDEX idx_execution_logs_rule_key ON execution_logs(rule_key);
CREATE INDEX idx_execution_logs_created_at ON execution_logs(created_at);
CREATE INDEX idx_execution_logs_status ON execution_logs(status);
CREATE INDEX idx_execution_logs_rule_key_created_at ON execution_logs(rule_key, created_at DESC);

-- 创建注释
COMMENT ON TABLE execution_logs IS '规则执行日志表';
COMMENT ON COLUMN execution_logs.rule_key IS '规则Key';
COMMENT ON COLUMN execution_logs.rule_version IS '规则版本号';
COMMENT ON COLUMN execution_logs.input_features IS '输入特征（JSONB格式）';
COMMENT ON COLUMN execution_logs.output_decision IS '决策结果：PASS/REJECT等';
COMMENT ON COLUMN execution_logs.output_reason IS '决策原因';
COMMENT ON COLUMN execution_logs.execution_time_ms IS '执行耗时（毫秒）';
COMMENT ON COLUMN execution_logs.status IS '执行状态：SUCCESS/TIMEOUT/ERROR';
COMMENT ON COLUMN execution_logs.error_message IS '错误信息（status为ERROR时记录）';
COMMENT ON COLUMN execution_logs.created_at IS '创建时间';
