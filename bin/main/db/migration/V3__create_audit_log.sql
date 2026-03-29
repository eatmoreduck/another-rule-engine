-- V3__create_audit_log.sql
-- 创建审计日志表

-- 创建审计日志表
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    operation_detail TEXT,
    operator VARCHAR(255) NOT NULL,
    operator_ip VARCHAR(50),
    operation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'SUCCESS',
    error_message TEXT,
    request_id VARCHAR(100)
);

-- 创建索引以提升查询性能
CREATE INDEX idx_audit_logs_entity_type_id ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_operation ON audit_logs(operation);
CREATE INDEX idx_audit_logs_operation_time ON audit_logs(operation_time);
CREATE INDEX idx_audit_logs_operator ON audit_logs(operator);
CREATE INDEX idx_audit_logs_request_id ON audit_logs(request_id);

-- 创建注释
COMMENT ON TABLE audit_logs IS '审计日志表';
COMMENT ON COLUMN audit_logs.entity_type IS '实体类型：RULE, VERSION, CONFIG等';
COMMENT ON COLUMN audit_logs.entity_id IS '实体ID';
COMMENT ON COLUMN audit_logs.operation IS '操作类型：CREATE, UPDATE, DELETE, ROLLBACK, VIEW等';
COMMENT ON COLUMN audit_logs.operation_detail IS '操作详情JSON';
COMMENT ON COLUMN audit_logs.operator IS '操作人';
COMMENT ON COLUMN audit_logs.operator_ip IS '操作人IP';
COMMENT ON COLUMN audit_logs.operation_time IS '操作时间';
COMMENT ON COLUMN audit_logs.status IS '状态：SUCCESS, FAILED';
COMMENT ON COLUMN audit_logs.error_message IS '错误信息';
COMMENT ON COLUMN audit_logs.request_id IS '请求ID，用于关联多个操作';
