-- 名单增加 list_key 字段，按决策流隔离
ALTER TABLE name_list ADD COLUMN IF NOT EXISTS list_key VARCHAR(255) NOT NULL DEFAULT 'GLOBAL';

-- 删除旧唯一约束，重建带 list_key 的新约束
ALTER TABLE name_list DROP CONSTRAINT IF EXISTS uq_nl_type_key;
ALTER TABLE name_list ADD CONSTRAINT uq_nl_list_type_key UNIQUE (list_key, list_type, key_type, key_value);

-- 重建索引
DROP INDEX IF EXISTS idx_nl_lookup;
CREATE INDEX idx_nl_lookup ON name_list (list_key, list_type, key_type, key_value);

COMMENT ON COLUMN name_list.list_key IS '名单 Key，通常为决策流 flowKey，GLOBAL 表示全局共享';
