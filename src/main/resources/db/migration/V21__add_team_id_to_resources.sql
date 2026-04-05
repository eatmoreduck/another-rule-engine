-- V21__add_team_id_to_resources.sql
-- 为规则和决策流表添加 team_id 列（允许 NULL，向后兼容）

-- 1. 规则表添加 team_id
ALTER TABLE rules ADD COLUMN IF NOT EXISTS team_id BIGINT;
COMMENT ON COLUMN rules.team_id IS '所属团队ID，NULL表示全局资源（所有人可见）';

-- 2. 决策流表添加 team_id
ALTER TABLE decision_flows ADD COLUMN IF NOT EXISTS team_id BIGINT;
COMMENT ON COLUMN decision_flows.team_id IS '所属团队ID，NULL表示全局资源（所有人可见）';

-- 索引（提升团队过滤查询性能）
CREATE INDEX IF NOT EXISTS idx_rules_team_id ON rules(team_id);
CREATE INDEX IF NOT EXISTS idx_decision_flows_team_id ON decision_flows(team_id);
