-- V2__create_version_history.sql
-- 创建规则版本历史表

-- 创建规则版本历史表
CREATE TABLE IF NOT EXISTS rule_versions (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    rule_key VARCHAR(255) NOT NULL,
    version INT NOT NULL,
    groovy_script TEXT NOT NULL,
    change_reason TEXT,
    changed_by VARCHAR(255) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_rollback BOOLEAN DEFAULT FALSE,
    rollback_from_version INT,
    CONSTRAINT fk_rule_versions_rule_id FOREIGN KEY (rule_id) REFERENCES rules(id) ON DELETE CASCADE,
    CONSTRAINT uq_rule_versions_rule_id_version UNIQUE (rule_id, version)
);

-- 创建索引以提升查询性能
CREATE INDEX idx_rule_versions_rule_id ON rule_versions(rule_id);
CREATE INDEX idx_rule_versions_rule_key ON rule_versions(rule_key);
CREATE INDEX idx_rule_versions_version ON rule_versions(version);
CREATE INDEX idx_rule_versions_rule_id_version ON rule_versions(rule_id, version);

-- 创建注释
COMMENT ON TABLE rule_versions IS '规则版本历史表';
COMMENT ON COLUMN rule_versions.rule_id IS '关联规则ID';
COMMENT ON COLUMN rule_versions.rule_key IS '规则标识（冗余字段便于查询）';
COMMENT ON COLUMN rule_versions.version IS '版本号';
COMMENT ON COLUMN rule_versions.groovy_script IS '该版本的 Groovy 脚本';
COMMENT ON COLUMN rule_versions.change_reason IS '变更原因';
COMMENT ON COLUMN rule_versions.changed_by IS '变更人';
COMMENT ON COLUMN rule_versions.changed_at IS '变更时间';
COMMENT ON COLUMN rule_versions.is_rollback IS '是否为回滚操作';
COMMENT ON COLUMN rule_versions.rollback_from_version IS '回滚来源版本';
