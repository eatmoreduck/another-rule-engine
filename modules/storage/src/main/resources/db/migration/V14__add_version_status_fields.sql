-- V14__add_version_status_fields.sql
-- 灰度发布 Phase 1: 为版本机制增加状态管理能力

-- 1. 规则版本表增加 status 字段
ALTER TABLE rule_versions ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';
-- 所有历史版本默认标记为 ARCHIVED（已归档），它们不是当前生效的版本
UPDATE rule_versions SET status = 'ARCHIVED';

-- 2. 决策流版本表增加 status 字段
ALTER TABLE decision_flow_versions ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';
UPDATE decision_flow_versions SET status = 'ARCHIVED';

-- 3. 主表增加 active_version 字段（显式标记当前生效版本号）
ALTER TABLE rules ADD COLUMN IF NOT EXISTS active_version INT;
UPDATE rules SET active_version = version;
ALTER TABLE decision_flows ADD COLUMN IF NOT EXISTS active_version INT;
UPDATE decision_flows SET active_version = version;

-- 4. 灰度配置表扩展支持决策流
ALTER TABLE grayscale_configs ADD COLUMN IF NOT EXISTS target_type VARCHAR(20) DEFAULT 'RULE';
ALTER TABLE grayscale_configs ADD COLUMN IF NOT EXISTS target_key VARCHAR(255);
-- 迁移现有数据：target_key = rule_key
UPDATE grayscale_configs SET target_key = rule_key WHERE target_key IS NULL;

-- 5. 索引
CREATE INDEX IF NOT EXISTS idx_rule_versions_key_status ON rule_versions(rule_key, status);
CREATE INDEX IF NOT EXISTS idx_df_versions_key_status ON decision_flow_versions(flow_key, status);
CREATE INDEX IF NOT EXISTS idx_grayscale_target ON grayscale_configs(target_type, target_key, status);

-- 6. 注释
COMMENT ON COLUMN rule_versions.status IS '版本状态: DRAFT/CANARY/ACTIVE/ARCHIVED';
COMMENT ON COLUMN decision_flow_versions.status IS '版本状态: DRAFT/CANARY/ACTIVE/ARCHIVED';
COMMENT ON COLUMN rules.active_version IS '当前生效的版本号';
COMMENT ON COLUMN decision_flows.active_version IS '当前生效的版本号';
COMMENT ON COLUMN grayscale_configs.target_type IS '灰度目标类型: RULE/DECISION_FLOW';
COMMENT ON COLUMN grayscale_configs.target_key IS '灰度目标Key（ruleKey 或 flowKey）';
