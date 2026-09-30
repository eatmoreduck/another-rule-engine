-- V22: 灰度配置表添加描述字段
ALTER TABLE grayscale_configs ADD COLUMN IF NOT EXISTS description TEXT;

COMMENT ON COLUMN grayscale_configs.description IS '灰度描述';
