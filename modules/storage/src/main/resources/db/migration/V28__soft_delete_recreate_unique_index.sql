-- V28: 规则/决策流的 Key 唯一约束改为部分唯一索引（仅约束未删除行）
-- 背景：逻辑删除（deleted=true / status='DELETED'）的行占用全行唯一索引，
--       导致删除后无法重建同名资源（INSERT 撞唯一约束返回 500）。
--       改为部分唯一索引后：未删除行保持 Key 唯一，已删除行不阻塞同名重建。

ALTER TABLE rules DROP CONSTRAINT rules_rule_key_key;
CREATE UNIQUE INDEX rules_rule_key_active_uidx ON rules (rule_key) WHERE deleted = false;

ALTER TABLE decision_flows DROP CONSTRAINT decision_flows_flow_key_key;
CREATE UNIQUE INDEX decision_flows_flow_key_active_uidx ON decision_flows (flow_key) WHERE status <> 'DELETED';
