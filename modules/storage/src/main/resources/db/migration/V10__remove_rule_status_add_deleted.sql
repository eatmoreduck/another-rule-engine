-- 规则不再有状态生命周期，由决策流管理生命周期
-- 添加软删除标记
ALTER TABLE rules ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT false;

-- 将已有的 DELETED 状态标记为 deleted=true
UPDATE rules SET deleted = true WHERE status = 'DELETED';

-- 删除 status 相关约束和索引
ALTER TABLE rules DROP CONSTRAINT IF EXISTS check_status;
DROP INDEX IF EXISTS idx_rules_status;
DROP INDEX IF EXISTS idx_rules_status_enabled;

-- 删除 status 列
ALTER TABLE rules DROP COLUMN IF EXISTS status;

-- 添加 deleted 索引
CREATE INDEX IF NOT EXISTS idx_rules_deleted ON rules(deleted);
