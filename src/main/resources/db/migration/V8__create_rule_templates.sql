-- V8__create_rule_templates.sql
-- 创建规则模板库

-- 系统预置模板表
CREATE TABLE IF NOT EXISTS rule_templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    description TEXT,
    groovy_template TEXT NOT NULL,
    parameters JSONB,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- 用户自定义模板表
CREATE TABLE IF NOT EXISTS custom_templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    groovy_template TEXT NOT NULL,
    parameters JSONB,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_rule_templates_category ON rule_templates(category);
CREATE INDEX idx_rule_templates_is_system ON rule_templates(is_system);
CREATE INDEX idx_custom_templates_created_by ON custom_templates(created_by);

-- 添加注释
COMMENT ON TABLE rule_templates IS '系统预置规则模板表';
COMMENT ON COLUMN rule_templates.name IS '模板名称';
COMMENT ON COLUMN rule_templates.category IS '模板分类';
COMMENT ON COLUMN rule_templates.description IS '模板描述';
COMMENT ON COLUMN rule_templates.groovy_template IS 'Groovy模板脚本(支持占位符)';
COMMENT ON COLUMN rule_templates.parameters IS '模板参数定义(JSONB)';
COMMENT ON COLUMN rule_templates.is_system IS '是否系统预置模板';
COMMENT ON COLUMN rule_templates.created_by IS '创建人';

COMMENT ON TABLE custom_templates IS '用户自定义规则模板表';
COMMENT ON COLUMN custom_templates.name IS '模板名称';
COMMENT ON COLUMN custom_templates.description IS '模板描述';
COMMENT ON COLUMN custom_templates.groovy_template IS 'Groovy模板脚本';
COMMENT ON COLUMN custom_templates.parameters IS '模板参数定义(JSONB)';
COMMENT ON COLUMN custom_templates.created_by IS '创建人';

-- 预置反欺诈规则模板
INSERT INTO rule_templates (name, category, description, groovy_template, parameters, is_system, created_by) VALUES
('金额阈值检查', '交易风控', '检查交易金额是否超过阈值',
 'def evaluate(context) {\n  def amount = context.amount as BigDecimal\n  def threshold = {{threshold}} as BigDecimal\n  if (amount > threshold) {\n    return [hit: true, action: "REJECT", reason: "交易金额${amount}超过阈值${threshold}"]\n  }\n  return [hit: false, action: "PASS"]\n}',
 '[{"name":"threshold","type":"BigDecimal","description":"金额阈值","required":true,"defaultValue":"10000"}]',
 true, 'system'),

('IP频率限制', '访问控制', '限制同一IP在时间窗口内的请求频率',
 'def evaluate(context) {\n  def ip = context.ip\n  def count = context.ipRequestCount as int\n  def limit = {{limit}} as int\n  def windowMinutes = {{windowMinutes}} as int\n  if (count > limit) {\n    return [hit: true, action: "REJECT", reason: "IP ${ip}在${windowMinutes}分钟内请求${count}次，超过限制${limit}"]\n  }\n  return [hit: false, action: "PASS"]\n}',
 '[{"name":"limit","type":"int","description":"请求次数上限","required":true,"defaultValue":"100"},{"name":"windowMinutes","type":"int","description":"时间窗口(分钟)","required":true,"defaultValue":"5"}]',
 true, 'system'),

('设备指纹异常检测', '反欺诈', '检测设备指纹是否存在异常',
 'def evaluate(context) {\n  def deviceId = context.deviceId\n  def deviceCount = context.deviceBindCount as int\n  def maxDevices = {{maxDevices}} as int\n  if (deviceCount > maxDevices) {\n    return [hit: true, action: "REVIEW", reason: "设备${deviceId}关联${deviceCount}个账号，超过阈值${maxDevices}"]\n  }\n  return [hit: false, action: "PASS"]\n}',
 '[{"name":"maxDevices","type":"int","description":"最大关联设备数","required":true,"defaultValue":"3"}]',
 true, 'system'),

('黑名单检查', '名单管理', '检查用户/卡号/IP是否在黑名单中',
 'def evaluate(context) {\n  def userId = context.userId\n  def blacklist = context.blacklist as Set\n  if (blacklist.contains(userId)) {\n    return [hit: true, action: "REJECT", reason: "用户${userId}在黑名单中"]\n  }\n  return [hit: false, action: "PASS"]\n}',
 '[]',
 true, 'system'),

('时间窗口交易统计', '统计分析', '统计用户在时间窗口内的交易次数和总金额',
 'def evaluate(context) {\n  def totalAmount = context.windowTotalAmount as BigDecimal\n  def totalCount = context.windowTotalCount as int\n  def amountLimit = {{amountLimit}} as BigDecimal\n  def countLimit = {{countLimit}} as int\n  if (totalAmount > amountLimit || totalCount > countLimit) {\n    return [hit: true, action: "REVIEW", reason: "窗口内交易总额${totalAmount}或次数${totalCount}超限"]\n  }\n  return [hit: false, action: "PASS"]\n}',
 '[{"name":"amountLimit","type":"BigDecimal","description":"金额上限","required":true,"defaultValue":"50000"},{"name":"countLimit","type":"int","description":"次数上限","required":true,"defaultValue":"20"}]',
 true, 'system');
