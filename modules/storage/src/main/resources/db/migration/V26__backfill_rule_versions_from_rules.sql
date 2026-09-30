-- V26__backfill_rule_versions_from_rules.sql
-- 旧数据回填：rules.groovy_script 主表列 → rule_versions（新后端以 rule_versions 为定义载荷唯一事实源）。
--
-- 背景：旧单体存在「主表有脚本、版本表无对应行」的历史数据（2b 批次遗留事项）。
-- 新 admin-api 的规则响应以当前生效版本的载荷回填 groovyScript 字段，
-- 缺版本行会导致旧规则的脚本在 API 响应中为空。
--
-- 幂等性：只为缺失 (rule_key, version) 行的规则补建，已有版本数据一律不触碰；
-- V23 的 (rule_key, version) 唯一约束保证补建不会与现存行冲突。
-- 状态推导：active_version 指向的版本补为 ACTIVE，其余补为 ARCHIVED。

INSERT INTO rule_versions (rule_id, rule_key, version, groovy_script, change_reason, changed_by, changed_at, status)
SELECT r.id,
       r.rule_key,
       COALESCE(r.version, 1),
       r.groovy_script,
       'V26 旧数据回填（主表脚本 → 版本载荷）',
       'migration',
       CURRENT_TIMESTAMP,
       CASE
           WHEN COALESCE(r.active_version, r.version, 1) = COALESCE(r.version, 1) THEN 'ACTIVE'
           ELSE 'ARCHIVED'
       END
FROM rules r
WHERE COALESCE(r.groovy_script, '') <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM rule_versions rv
      WHERE rv.rule_key = r.rule_key
        AND rv.version = COALESCE(r.version, 1)
  );
