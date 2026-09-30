-- 性能优化：为规则表添加索引
-- 目标：优化规则加载性能，确保 P95 < 10ms

-- 1. 为 rule_key 字段创建唯一索引（如果尚未创建）
-- 注意：rule_key 已经是唯一约束，这里确保有索引
CREATE INDEX IF NOT EXISTS idx_rules_rule_key ON rules(rule_key);

-- 2. 为 enabled 字段创建索引
-- 用途：优化查询启用的规则
CREATE INDEX IF NOT EXISTS idx_rules_enabled ON rules(enabled);

-- 3. 为 status 字段创建索引
-- 用途：优化按状态查询规则
CREATE INDEX IF NOT EXISTS idx_rules_status ON rules(status);

-- 4. 创建复合索引：(enabled, status)
-- 用途：优化查询生效中的规则（enabled=true AND status='ACTIVE'）
CREATE INDEX IF NOT EXISTS idx_rules_enabled_status ON rules(enabled, status);

-- 5. 为 created_by 字段创建索引
-- 用途：优化按创建人查询规则
CREATE INDEX IF NOT EXISTS idx_rules_created_by ON rules(created_by);

-- 6. 为 created_at 字段创建索引
-- 用途：优化按创建时间排序查询
CREATE INDEX IF NOT EXISTS idx_rules_created_at ON rules(created_at);

-- 7. 为 updated_at 字段创建索引
-- 用途：优化按更新时间排序查询
CREATE INDEX IF NOT EXISTS idx_rules_updated_at ON rules(updated_at);

-- 8. 为 (status, created_at) 创建复合索引
-- 用途：优化按状态和创建时间组合查询
CREATE INDEX IF NOT EXISTS idx_rules_status_created_at ON rules(status, created_at);

-- 9. 为 (created_by, status) 创建复合索引
-- 用途：优化按创建人和状态组合查询
CREATE INDEX IF NOT EXISTS idx_rules_created_by_status ON rules(created_by, status);

-- 10. 为版本历史表添加索引
-- 10.1 为 rule_key 字段创建索引
CREATE INDEX IF NOT EXISTS idx_rule_versions_rule_key ON rule_versions(rule_key);

-- 10.2 为 version 字段创建索引
CREATE INDEX IF NOT EXISTS idx_rule_versions_version ON rule_versions(version);

-- 10.3 为 changed_at 字段创建索引
CREATE INDEX IF NOT EXISTS idx_rule_versions_changed_at ON rule_versions(changed_at);

-- 10.4 创建复合索引：(rule_key, version)
-- 用途：优化查询指定规则的特定版本
CREATE INDEX IF NOT EXISTS idx_rule_versions_rule_key_version ON rule_versions(rule_key, version DESC);

-- 11. 为审计日志表添加索引
-- 11.1 为 (entity_type, entity_id) 创建复合索引
-- 用途：优化查询指定实体的审计日志
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity_type_id ON audit_logs(entity_type, entity_id);

-- 11.2 为 operation 字段创建索引
-- 用途：优化按操作类型查询审计日志
CREATE INDEX IF NOT EXISTS idx_audit_logs_operation ON audit_logs(operation);

-- 11.3 为 operation_time 字段创建索引
-- 用途：优化按操作时间排序查询
CREATE INDEX IF NOT EXISTS idx_audit_logs_operation_time ON audit_logs(operation_time);

-- 11.4 为 operator 字段创建索引
-- 用途：优化按操作人查询审计日志
CREATE INDEX IF NOT EXISTS idx_audit_logs_operator ON audit_logs(operator);

-- 11.5 创建复合索引：(entity_type, entity_id, operation_time)
-- 用途：优化查询指定实体的审计历史
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity_time ON audit_logs(entity_type, entity_id, operation_time DESC);

-- 注释：索引策略
-- 1. 单列索引：用于单个字段的查询优化
-- 2. 复合索引：用于多字段组合查询，注意字段顺序（高选择性字段在前）
-- 3. DESC 索引：优化时间倒序排序查询（最常见的场景）
-- 4. 部分索引：可根据需要添加 WHERE 条件（例如：WHERE enabled = true）

-- 性能预期：
-- - 查询单条规则（通过 rule_key）：< 1ms
-- - 查询生效中的规则：< 5ms
-- - 批量查询规则（10 条）：< 10ms
-- - 分页查询规则：< 20ms
