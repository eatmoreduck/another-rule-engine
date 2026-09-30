-- 将 scope_key 列重命名为 list_key
--
-- ⚠️ 相对旧后端原件（src/main/resources/db/migration/V13）的兼容性修正：
-- 原件第一行为无条件 RENAME，但修正后基线中 V12 已直接创建 list_key 列（无 scope_key），
-- 干净库按 V1..V25 顺序重放时 RENAME 报 42703（column "scope_key" does not exist）。
-- 此副本按 information_schema 探测做幂等处理，最终 schema 不变：
-- name_list.list_key VARCHAR(255) NOT NULL DEFAULT 'GLOBAL'。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'name_list' AND column_name = 'scope_key'
    ) THEN
        ALTER TABLE name_list RENAME COLUMN scope_key TO list_key;
    END IF;
END $$;

-- 重建约束和索引（名称从 uq_nl_scope_type_key 改为 uq_nl_list_type_key；先删后建保证幂等：
-- V12 路径下 uq_nl_list_type_key 已存在）
ALTER TABLE name_list DROP CONSTRAINT IF EXISTS uq_nl_scope_type_key;
ALTER TABLE name_list DROP CONSTRAINT IF EXISTS uq_nl_list_type_key;
ALTER TABLE name_list ADD CONSTRAINT uq_nl_list_type_key UNIQUE (list_key, list_type, key_type, key_value);

-- 重建索引
DROP INDEX IF EXISTS idx_nl_lookup;
CREATE INDEX idx_nl_lookup ON name_list (list_key, list_type, key_type, key_value);

COMMENT ON COLUMN name_list.list_key IS '名单 Key，通常为决策流 flowKey，GLOBAL 表示全局共享';
