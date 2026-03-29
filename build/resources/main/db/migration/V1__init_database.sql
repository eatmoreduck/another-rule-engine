-- V1__init_database.sql
-- 创建规则引擎基础表结构

-- 创建规则主表
CREATE TABLE IF NOT EXISTS rules (
    id BIGSERIAL PRIMARY KEY,
    rule_key VARCHAR(255) UNIQUE NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    rule_description TEXT,
    groovy_script TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255),
    updated_at TIMESTAMP,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    opt_lock_version BIGINT DEFAULT 0,
    CONSTRAINT check_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED', 'DELETED'))
);

-- 创建索引以提升查询性能
CREATE INDEX idx_rules_rule_key ON rules(rule_key);
CREATE INDEX idx_rules_status ON rules(status);
CREATE INDEX idx_rules_enabled ON rules(enabled);
CREATE INDEX idx_rules_status_enabled ON rules(status, enabled);

-- 创建注释
COMMENT ON TABLE rules IS '规则主表';
COMMENT ON COLUMN rules.rule_key IS '规则唯一标识';
COMMENT ON COLUMN rules.rule_name IS '规则名称';
COMMENT ON COLUMN rules.rule_description IS '规则描述';
COMMENT ON COLUMN rules.groovy_script IS 'Groovy DSL 脚本';
COMMENT ON COLUMN rules.version IS '当前版本号';
COMMENT ON COLUMN rules.status IS '规则状态: DRAFT-草稿, ACTIVE-生效中, ARCHIVED-已归档, DELETED-已删除';
COMMENT ON COLUMN rules.created_by IS '创建人';
COMMENT ON COLUMN rules.created_at IS '创建时间';
COMMENT ON COLUMN rules.updated_by IS '更新人';
COMMENT ON COLUMN rules.updated_at IS '更新时间';
COMMENT ON COLUMN rules.enabled IS '是否启用';
