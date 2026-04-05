-- V18__add_grayscale_strategy_fields.sql
-- 灰度发布 Phase 2: 灰度配置增加策略相关字段

-- 1. 策略类型：PERCENTAGE(百分比) / FEATURE(特征匹配) / WHITELIST(白名单)
ALTER TABLE grayscale_configs ADD COLUMN IF NOT EXISTS strategy_type VARCHAR(30) DEFAULT 'PERCENTAGE';

-- 2. 特征匹配规则（JSON 格式，仅 FEATURE 策略使用）
ALTER TABLE grayscale_configs ADD COLUMN IF NOT EXISTS feature_rules TEXT;

-- 3. 白名单用户ID列表（逗号分隔，仅 WHITELIST 策略使用）
ALTER TABLE grayscale_configs ADD COLUMN IF NOT EXISTS whitelist_ids TEXT;

-- 4. 是否启用双跑对比
ALTER TABLE grayscale_configs ADD COLUMN IF NOT EXISTS dual_run_enabled BOOLEAN DEFAULT FALSE;

-- 5. 注释
COMMENT ON COLUMN grayscale_configs.strategy_type IS '灰度策略类型: PERCENTAGE/FEATURE/WHITELIST';
COMMENT ON COLUMN grayscale_configs.feature_rules IS '特征匹配规则(JSON格式)';
COMMENT ON COLUMN grayscale_configs.whitelist_ids IS '白名单用户ID列表(逗号分隔)';
COMMENT ON COLUMN grayscale_configs.dual_run_enabled IS '是否启用双跑对比模式';
