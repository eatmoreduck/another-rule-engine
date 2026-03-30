-- V7__create_environments.sql
-- 创建环境隔离支持

-- 环境表
CREATE TABLE IF NOT EXISTS environments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_environments_name UNIQUE (name),
    CONSTRAINT chk_environment_type CHECK (type IN ('DEV', 'STAGING', 'PRODUCTION'))
);

-- rules 表添加 environment_id 列
ALTER TABLE rules ADD COLUMN IF NOT EXISTS environment_id BIGINT;
ALTER TABLE rules ADD CONSTRAINT fk_rules_environment FOREIGN KEY (environment_id) REFERENCES environments(id);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_rules_environment_id ON rules(environment_id);
CREATE INDEX IF NOT EXISTS idx_environments_type ON environments(type);

-- 插入默认环境
INSERT INTO environments (name, type, description) VALUES ('DEV', 'DEV', '开发环境');
INSERT INTO environments (name, type, description) VALUES ('STAGING', 'STAGING', '预发布环境');
INSERT INTO environments (name, type, description) VALUES ('PRODUCTION', 'PRODUCTION', '生产环境');

-- 添加注释
COMMENT ON TABLE environments IS '环境配置表';
COMMENT ON COLUMN environments.name IS '环境名称';
COMMENT ON COLUMN environments.type IS '环境类型: DEV/STAGING/PRODUCTION';
COMMENT ON COLUMN environments.description IS '环境描述';
