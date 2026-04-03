-- 将 scope_key 列重命名为 list_key
ALTER TABLE name_list RENAME COLUMN scope_key TO list_key;

-- 重建约束和索引（名称从 uq_nl_scope_type_key 改为 uq_nl_list_type_key）
ALTER TABLE name_list DROP CONSTRAINT IF EXISTS uq_nl_scope_type_key;
ALTER TABLE name_list ADD CONSTRAINT uq_nl_list_type_key UNIQUE (list_key, list_type, key_type, key_value);

-- 重建索引
DROP INDEX IF EXISTS idx_nl_lookup;
CREATE INDEX idx_nl_lookup ON name_list (list_key, list_type, key_type, key_value);

COMMENT ON COLUMN name_list.list_key IS '名单 Key，通常为决策流 flowKey，GLOBAL 表示全局共享';
