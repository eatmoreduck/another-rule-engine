-- V12 already added the column as list_key (not scope_key), so the rename is a no-op.
-- Ensure constraint and index exist with the correct names.
ALTER TABLE name_list DROP CONSTRAINT IF EXISTS uq_nl_scope_type_key;
ALTER TABLE name_list DROP CONSTRAINT IF EXISTS uq_nl_list_type_key;
ALTER TABLE name_list ADD CONSTRAINT uq_nl_list_type_key UNIQUE (list_key, list_type, key_type, key_value);

DROP INDEX IF EXISTS idx_nl_lookup;
CREATE INDEX idx_nl_lookup ON name_list (list_key, list_type, key_type, key_value);
