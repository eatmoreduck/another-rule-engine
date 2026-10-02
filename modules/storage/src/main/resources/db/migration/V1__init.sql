-- =============================================================================
-- V1__init.sql — 规则引擎数据库初始化（唯一迁移，全新建库一次执行）
--
-- 2026-10 项目重启：历史渐进迁移（原 V1..V28）收敛为本脚本。
-- 全部表结构/索引/种子数据均为收敛时点的最终形态（不再保留中间态 ALTER/DROP）：
--   - rules 无 status 生命周期列，软删除用 deleted 布尔列
--   - rule_key / flow_key 唯一性为部分唯一索引（仅约束未删除行，同名可重建）
--   - name_list 直接带 list_key（决策流隔离，GLOBAL 为全局）
--   - feature_definition 无 scope / sensitivity（历史遗留元数据，全链路无消费）
--   - 旧数据回填逻辑（原 V26）不适用于全新建库，已移除
--
-- 敏感项（用户密码）为 BCrypt 哈希：admin / admin123（仅默认种子，上线前必改）
-- =============================================================================

-- ─────────────────────────────────────────────────────────────
-- 1. 认证与权限域（sys_*）
-- ─────────────────────────────────────────────────────────────

-- 1.1 用户表
CREATE TABLE sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(100),
    email VARCHAR(200),
    phone VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_sys_user_status CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED'))
);

-- 1.2 角色表
CREATE TABLE sys_role (
    id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(100) UNIQUE NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_sys_role_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- 1.3 权限表
CREATE TABLE sys_permission (
    id BIGSERIAL PRIMARY KEY,
    permission_code VARCHAR(200) UNIQUE NOT NULL,
    permission_name VARCHAR(200) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_path VARCHAR(500),
    method VARCHAR(10),
    parent_id BIGINT,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sys_perm_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_sys_perm_type CHECK (resource_type IN ('MENU', 'BUTTON', 'API'))
);

-- 1.4 用户-角色关联
CREATE TABLE sys_user_role (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT uq_sys_user_role UNIQUE (user_id, role_id)
);

-- 1.5 角色-权限关联
CREATE TABLE sys_role_permission (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sys_role_perm_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_sys_role_perm_perm FOREIGN KEY (permission_id) REFERENCES sys_permission(id) ON DELETE CASCADE,
    CONSTRAINT uq_sys_role_perm UNIQUE (role_id, permission_id)
);

-- 1.6 团队与用户-团队关联（数据权限隔离）
CREATE TABLE sys_team (
    id BIGSERIAL PRIMARY KEY,
    team_code VARCHAR(100) UNIQUE NOT NULL,
    team_name VARCHAR(200) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE sys_user_team (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    team_id BIGINT NOT NULL REFERENCES sys_team(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_team UNIQUE (user_id, team_id)
);

CREATE INDEX idx_sys_user_username ON sys_user(username);
CREATE INDEX idx_sys_user_status ON sys_user(status);
CREATE INDEX idx_sys_role_code ON sys_role(role_code);
CREATE INDEX idx_sys_perm_code ON sys_permission(permission_code);
CREATE INDEX idx_sys_perm_parent ON sys_permission(parent_id);
CREATE INDEX idx_sys_user_role_user ON sys_user_role(user_id);
CREATE INDEX idx_sys_user_role_role ON sys_user_role(role_id);
CREATE INDEX idx_sys_role_perm_role ON sys_role_permission(role_id);
CREATE INDEX idx_sys_role_perm_perm ON sys_role_permission(permission_id);
CREATE INDEX idx_sys_user_team_user_id ON sys_user_team(user_id);
CREATE INDEX idx_sys_user_team_team_id ON sys_user_team(team_id);

COMMENT ON TABLE sys_user IS '系统用户表';
COMMENT ON COLUMN sys_user.password IS '密码（BCrypt 加密）';
COMMENT ON COLUMN sys_user.status IS '状态: ACTIVE-正常, DISABLED-禁用, LOCKED-锁定';
COMMENT ON TABLE sys_role IS '系统角色表';
COMMENT ON TABLE sys_permission IS '系统权限表';
COMMENT ON COLUMN sys_permission.resource_type IS '资源类型: MENU-菜单, BUTTON-按钮, API-接口';
COMMENT ON TABLE sys_user_role IS '用户-角色关联表';
COMMENT ON TABLE sys_role_permission IS '角色-权限关联表';
COMMENT ON TABLE sys_team IS '团队表（数据权限隔离）';
COMMENT ON TABLE sys_user_team IS '用户-团队关联表';

-- ─────────────────────────────────────────────────────────────
-- 2. 环境域
-- ─────────────────────────────────────────────────────────────

CREATE TABLE environments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_environments_name UNIQUE (name),
    CONSTRAINT chk_environment_type CHECK (type IN ('DEV', 'STAGING', 'PRODUCTION'))
);

CREATE INDEX idx_environments_type ON environments(type);

INSERT INTO environments (name, type, description) VALUES
    ('DEV', 'DEV', '开发环境'),
    ('STAGING', 'STAGING', '预发布环境'),
    ('PRODUCTION', 'PRODUCTION', '生产环境');

COMMENT ON TABLE environments IS '环境配置表';

-- ─────────────────────────────────────────────────────────────
-- 3. 规则域（主表 + 版本历史）
-- ─────────────────────────────────────────────────────────────

-- 3.1 规则主表（最终形态：无 status 列，软删除用 deleted；Key 唯一只约束未删除行）
CREATE TABLE rules (
    id BIGSERIAL PRIMARY KEY,
    rule_key VARCHAR(255) NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    rule_description TEXT,
    groovy_script TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    active_version INT,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255),
    updated_at TIMESTAMP,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    opt_lock_version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    environment_id BIGINT,
    team_id BIGINT
);

-- 同名重建支持：部分唯一索引只约束未删除行（删除后可重建同名规则）
CREATE UNIQUE INDEX rules_rule_key_active_uidx ON rules (rule_key) WHERE deleted = FALSE;
CREATE INDEX idx_rules_rule_key ON rules(rule_key);
CREATE INDEX idx_rules_enabled ON rules(enabled);
CREATE INDEX idx_rules_deleted ON rules(deleted);
CREATE INDEX idx_rules_environment_id ON rules(environment_id);
CREATE INDEX idx_rules_team_id ON rules(team_id);

COMMENT ON TABLE rules IS '规则主表';
COMMENT ON COLUMN rules.rule_key IS '规则唯一标识（未删除行内唯一）';
COMMENT ON COLUMN rules.groovy_script IS 'Groovy 脚本';
COMMENT ON COLUMN rules.version IS '当前版本号';
COMMENT ON COLUMN rules.active_version IS '当前生效的版本号';
COMMENT ON COLUMN rules.deleted IS '软删除标记';
COMMENT ON COLUMN rules.team_id IS '所属团队ID，NULL表示全局资源（所有人可见）';

-- 3.2 规则版本历史（新后端以 rule_versions 为定义载荷唯一事实源）
CREATE TABLE rule_versions (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    rule_key VARCHAR(255) NOT NULL,
    version INT NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    groovy_script TEXT NOT NULL,
    change_reason TEXT,
    changed_by VARCHAR(255) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_rollback BOOLEAN DEFAULT FALSE,
    rollback_from_version INT,
    CONSTRAINT fk_rule_versions_rule_id FOREIGN KEY (rule_id) REFERENCES rules(id) ON DELETE CASCADE,
    CONSTRAINT uq_rule_versions_rule_id_version UNIQUE (rule_id, version),
    CONSTRAINT uq_rule_versions_rule_key_version UNIQUE (rule_key, version)
);

CREATE INDEX idx_rule_versions_rule_id ON rule_versions(rule_id);
CREATE INDEX idx_rule_versions_rule_key ON rule_versions(rule_key);
CREATE INDEX idx_rule_versions_version ON rule_versions(version);
CREATE INDEX idx_rule_versions_rule_id_version ON rule_versions(rule_id, version);
CREATE INDEX idx_rule_versions_key_status ON rule_versions(rule_key, status);

COMMENT ON TABLE rule_versions IS '规则版本历史表';
COMMENT ON COLUMN rule_versions.status IS '版本状态: DRAFT/CANARY/ACTIVE/ARCHIVED';
COMMENT ON COLUMN rule_versions.rule_key IS '规则标识（冗余字段便于查询）';

-- ─────────────────────────────────────────────────────────────
-- 4. 审计与执行日志域
-- ─────────────────────────────────────────────────────────────

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    operation_detail TEXT,
    operator VARCHAR(255) NOT NULL,
    operator_ip VARCHAR(50),
    operation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'SUCCESS',
    error_message TEXT,
    request_id VARCHAR(100)
);

CREATE INDEX idx_audit_logs_entity_type_id ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_operation ON audit_logs(operation);
CREATE INDEX idx_audit_logs_operation_time ON audit_logs(operation_time);
CREATE INDEX idx_audit_logs_operator ON audit_logs(operator);
CREATE INDEX idx_audit_logs_request_id ON audit_logs(request_id);

COMMENT ON TABLE audit_logs IS '审计日志表';
COMMENT ON COLUMN audit_logs.status IS '状态：SUCCESS, FAILED';

CREATE TABLE execution_logs (
    id BIGSERIAL PRIMARY KEY,
    rule_key VARCHAR(255) NOT NULL,
    rule_version INT,
    input_features JSONB,
    output_decision VARCHAR(50),
    output_reason TEXT,
    execution_time_ms INT,
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_execution_logs_rule_key ON execution_logs(rule_key);
CREATE INDEX idx_execution_logs_created_at ON execution_logs(created_at);
CREATE INDEX idx_execution_logs_status ON execution_logs(status);
CREATE INDEX idx_execution_logs_rule_key_created_at ON execution_logs(rule_key, created_at DESC);

COMMENT ON TABLE execution_logs IS '规则执行日志表';
COMMENT ON COLUMN execution_logs.input_features IS '输入特征（JSONB格式，应用写入时 URL 携带 stringtype=unspecified）';
COMMENT ON COLUMN execution_logs.status IS '执行状态：SUCCESS/TIMEOUT/ERROR';

-- ─────────────────────────────────────────────────────────────
-- 5. 灰度发布域
-- ─────────────────────────────────────────────────────────────

CREATE TABLE grayscale_configs (
    id BIGSERIAL PRIMARY KEY,
    rule_key VARCHAR(255) NOT NULL,
    current_version INT NOT NULL,
    grayscale_version INT NOT NULL,
    grayscale_percentage INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    target_type VARCHAR(20) DEFAULT 'RULE',
    target_key VARCHAR(255),
    strategy_type VARCHAR(30) DEFAULT 'PERCENTAGE',
    feature_rules TEXT,
    whitelist_ids TEXT,
    dual_run_enabled BOOLEAN DEFAULT FALSE,
    description TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_grayscale_configs_rule_key ON grayscale_configs(rule_key);
CREATE INDEX idx_grayscale_configs_status ON grayscale_configs(status);
CREATE INDEX idx_grayscale_configs_rule_key_status ON grayscale_configs(rule_key, status);
CREATE INDEX idx_grayscale_target ON grayscale_configs(target_type, target_key, status);

COMMENT ON TABLE grayscale_configs IS '灰度发布配置表';
COMMENT ON COLUMN grayscale_configs.status IS '灰度状态: DRAFT/RUNNING/COMPLETED/ROLLED_BACK';
COMMENT ON COLUMN grayscale_configs.target_type IS '灰度目标类型: RULE/DECISION_FLOW';
COMMENT ON COLUMN grayscale_configs.target_key IS '灰度目标Key（ruleKey 或 flowKey）';
COMMENT ON COLUMN grayscale_configs.strategy_type IS '灰度策略类型: PERCENTAGE/FEATURE/WHITELIST';
COMMENT ON COLUMN grayscale_configs.feature_rules IS '特征匹配规则(JSON格式)';
COMMENT ON COLUMN grayscale_configs.whitelist_ids IS '白名单用户ID列表(逗号分隔)';
COMMENT ON COLUMN grayscale_configs.dual_run_enabled IS '是否启用双跑对比模式';

CREATE TABLE grayscale_metrics (
    id BIGSERIAL PRIMARY KEY,
    grayscale_config_id BIGINT NOT NULL REFERENCES grayscale_configs(id),
    version INT NOT NULL,
    execution_count INT DEFAULT 0,
    hit_count INT DEFAULT 0,
    error_count INT DEFAULT 0,
    avg_execution_time_ms INT DEFAULT 0,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_grayscale_metrics_config_id ON grayscale_metrics(grayscale_config_id);
CREATE INDEX idx_grayscale_metrics_config_id_version ON grayscale_metrics(grayscale_config_id, version);

COMMENT ON TABLE grayscale_metrics IS '灰度指标表';

-- 灰度分流执行明细（效果对比与问题排查）
CREATE TABLE canary_execution_log (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    target_type VARCHAR(20) NOT NULL DEFAULT 'RULE',
    target_key VARCHAR(255) NOT NULL,
    version_used INT NOT NULL,
    is_canary BOOLEAN NOT NULL DEFAULT FALSE,
    request_features JSONB,
    decision_result VARCHAR(50),
    execution_time_ms BIGINT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_canary_log_trace_id ON canary_execution_log(trace_id);
CREATE INDEX idx_canary_log_target_time ON canary_execution_log(target_type, target_key, created_at);
CREATE INDEX idx_canary_log_is_canary ON canary_execution_log(is_canary);

COMMENT ON TABLE canary_execution_log IS '灰度分流执行日志表';

-- ─────────────────────────────────────────────────────────────
-- 6. 决策流域（主表 + 版本历史）
-- ─────────────────────────────────────────────────────────────

CREATE TABLE decision_flows (
    id BIGSERIAL PRIMARY KEY,
    flow_key VARCHAR(255) NOT NULL,
    flow_name VARCHAR(255) NOT NULL,
    flow_description TEXT,
    flow_graph TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    active_version INT,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255),
    updated_at TIMESTAMP,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    opt_lock_version BIGINT DEFAULT 0,
    environment_id BIGINT,
    team_id BIGINT,
    CONSTRAINT chk_df_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED', 'DELETED'))
);

-- 同名重建支持：部分唯一索引只约束非 DELETED 行
CREATE UNIQUE INDEX decision_flows_flow_key_active_uidx ON decision_flows (flow_key) WHERE status <> 'DELETED';
CREATE INDEX idx_df_flow_key ON decision_flows(flow_key);
CREATE INDEX idx_df_status ON decision_flows(status);
CREATE INDEX idx_df_enabled ON decision_flows(enabled);
CREATE INDEX idx_df_status_enabled ON decision_flows(status, enabled);
CREATE INDEX idx_decision_flows_team_id ON decision_flows(team_id);

COMMENT ON TABLE decision_flows IS '决策流主表';
COMMENT ON COLUMN decision_flows.flow_key IS '决策流唯一标识（非 DELETED 行内唯一）';
COMMENT ON COLUMN decision_flows.active_version IS '当前生效的版本号';
COMMENT ON COLUMN decision_flows.team_id IS '所属团队ID，NULL表示全局资源（所有人可见）';

CREATE TABLE decision_flow_versions (
    id BIGSERIAL PRIMARY KEY,
    flow_id BIGINT NOT NULL,
    flow_key VARCHAR(255) NOT NULL,
    version INT NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    flow_graph TEXT NOT NULL,
    change_reason TEXT,
    changed_by VARCHAR(255) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_rollback BOOLEAN DEFAULT FALSE,
    rollback_from_version INT,
    CONSTRAINT fk_dfv_flow FOREIGN KEY (flow_id) REFERENCES decision_flows(id)
);

CREATE INDEX idx_dfv_flow_id ON decision_flow_versions(flow_id);
CREATE INDEX idx_dfv_flow_key_version ON decision_flow_versions(flow_key, version);
CREATE INDEX idx_df_versions_key_status ON decision_flow_versions(flow_key, status);

COMMENT ON TABLE decision_flow_versions IS '决策流版本历史表';
COMMENT ON COLUMN decision_flow_versions.status IS '版本状态: DRAFT/CANARY/ACTIVE/ARCHIVED';

-- ─────────────────────────────────────────────────────────────
-- 7. 黑白名单域
-- ─────────────────────────────────────────────────────────────

CREATE TABLE name_list (
    id BIGSERIAL PRIMARY KEY,
    list_key VARCHAR(255) NOT NULL DEFAULT 'GLOBAL',
    list_type VARCHAR(10) NOT NULL,
    key_type VARCHAR(20) NOT NULL,
    key_value VARCHAR(256) NOT NULL,
    reason TEXT,
    source VARCHAR(255),
    expired_at TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255),
    updated_at TIMESTAMP,
    CONSTRAINT chk_nl_list_type CHECK (list_type IN ('BLACK', 'WHITE')),
    CONSTRAINT chk_nl_key_type CHECK (key_type IN ('ID_NO', 'DEVICE_ID', 'IP', 'PHONE_NO', 'MAC_ADDR')),
    CONSTRAINT uq_nl_list_type_key UNIQUE (list_key, list_type, key_type, key_value)
);

CREATE INDEX idx_nl_lookup ON name_list (list_key, list_type, key_type, key_value);
CREATE INDEX idx_nl_expired ON name_list (expired_at);

COMMENT ON TABLE name_list IS '黑名单/白名单';
COMMENT ON COLUMN name_list.list_key IS '名单 Key，通常为决策流 flowKey，GLOBAL 表示全局共享';
COMMENT ON COLUMN name_list.list_type IS 'BLACK-黑名单, WHITE-白名单';
COMMENT ON COLUMN name_list.key_type IS 'ID_NO, DEVICE_ID, IP, PHONE_NO, MAC_ADDR';

-- ─────────────────────────────────────────────────────────────
-- 8. 特征目录域
-- ─────────────────────────────────────────────────────────────

CREATE TABLE feature_definition (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(120) NOT NULL,
    name VARCHAR(200) NOT NULL,
    data_type VARCHAR(50) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    example_value TEXT,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    owner VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- 同名重建支持：部分唯一索引只约束未删除行（删除后可重建同名特征）
CREATE UNIQUE INDEX uq_feature_definition_code ON feature_definition (code) WHERE deleted = FALSE;
CREATE INDEX idx_feature_definition_status ON feature_definition (status);
CREATE INDEX idx_feature_definition_source_type ON feature_definition (source_type);

CREATE TABLE feature_alias (
    id BIGSERIAL PRIMARY KEY,
    alias_code VARCHAR(120) NOT NULL,
    canonical_code VARCHAR(120) NOT NULL,
    alias_type VARCHAR(50) NOT NULL DEFAULT 'LEGACY',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 注：旧设计在 canonical_code 上挂了指向 feature_definition(code) 的外键；code 改为
-- 部分唯一索引（软删除同名重建语义）后外键不再可行。别名行的完整性由应用层保证：
-- 写入仅经 FeatureCatalogService，特征软删除时别名行一并清理。

CREATE UNIQUE INDEX uq_feature_alias_alias_code ON feature_alias (alias_code);
CREATE INDEX idx_feature_alias_canonical_code ON feature_alias (canonical_code);

COMMENT ON TABLE feature_definition IS '特征定义（特征字典）';
COMMENT ON COLUMN feature_definition.deleted IS '软删除标记；别名行随删除一并物理清理（兼容映射随特征失效）';
COMMENT ON TABLE feature_alias IS '特征别名（历史编码 → 规范编码）';

INSERT INTO feature_definition (code, name, data_type, source_type, example_value, description, status, owner)
VALUES
    ('user_age', '用户年龄', 'NUMBER', 'INPUT', '25', '用户基础画像中的年龄特征', 'ACTIVE', 'system'),
    ('user_level', '用户等级', 'STRING', 'INPUT', 'VIP', '用户会员等级', 'ACTIVE', 'system'),
    ('order_amount', '订单金额', 'NUMBER', 'INPUT', '1288.88', '订单支付金额', 'ACTIVE', 'system'),
    ('risk_score', '风险分', 'NUMBER', 'DERIVED', '0.82', '上游模型输出的风险分', 'ACTIVE', 'system');

INSERT INTO feature_alias (alias_code, canonical_code, alias_type, status)
VALUES
    ('amount', 'order_amount', 'LEGACY', 'ACTIVE'),
    ('score', 'risk_score', 'LEGACY', 'ACTIVE');

-- ─────────────────────────────────────────────────────────────
-- 9. 种子数据：角色 / 权限 / 管理员
-- ─────────────────────────────────────────────────────────────

-- 9.1 角色
INSERT INTO sys_role (role_code, role_name, description) VALUES
    ('SUPER_ADMIN', '超级管理员', '拥有系统全部权限'),
    ('ADMIN', '管理员', '拥有大部分管理权限，不含系统设置'),
    ('OPERATOR', '运维人员', '规则管理、灰度发布等运维操作'),
    ('VIEWER', '只读用户', '仅查看权限，不可修改');

-- 9.2 菜单权限（排序与前端导航一致）
INSERT INTO sys_permission (permission_code, permission_name, resource_type, resource_path, sort_order) VALUES
    ('menu:rules', '规则配置', 'MENU', '/rules', 1),
    ('menu:decision-flows', '决策流', 'MENU', '/decision-flows', 2),
    ('menu:name-list', '名单管理', 'MENU', '/name-list', 3),
    ('menu:feature-catalog', '特征字典', 'MENU', '/feature-catalog', 4),
    ('menu:grayscale', '灰度发布', 'MENU', '/grayscale', 5),
    ('menu:monitoring', '监控仪表盘', 'MENU', '/monitoring', 6),
    ('menu:analytics', '分析中心', 'MENU', '/analytics', 7),
    ('menu:environments', '多环境', 'MENU', '/environments', 8),
    ('menu:import-export', '导入导出', 'MENU', '/import-export', 9),
    ('menu:settings', '系统设置', 'MENU', '/settings', 10);

-- 9.3 API 权限（parent_id 经 permission_code 关联到所属菜单，不依赖自增 id 顺序）
INSERT INTO sys_permission (permission_code, permission_name, resource_type, resource_path, method, parent_id, sort_order)
SELECT v.code, v.name, 'API', v.path, v.method, m.id, v.ord
FROM (VALUES
    ('api:rules:create',    '创建规则', '/api/v1/rules', 'POST',   'menu:rules', 1),
    ('api:rules:update',    '更新规则', '/api/v1/rules/*', 'PUT',  'menu:rules', 2),
    ('api:rules:delete',    '删除规则', '/api/v1/rules/*', 'DELETE', 'menu:rules', 3),
    ('api:rules:enable',    '启用规则', '/api/v1/rules/*/enable', 'POST', 'menu:rules', 4),
    ('api:rules:disable',   '禁用规则', '/api/v1/rules/*/disable', 'POST', 'menu:rules', 5),
    ('api:rules:view',      '查看规则', '/api/v1/rules', 'GET',    'menu:rules', 6),
    ('api:rules:validate',  '验证脚本', '/api/v1/rules/validate', 'POST', 'menu:rules', 7),
    ('api:decision-flows:create', '创建决策流', '/api/v1/decision-flows', 'POST', 'menu:decision-flows', 1),
    ('api:decision-flows:update', '更新决策流', '/api/v1/decision-flows/*', 'PUT', 'menu:decision-flows', 2),
    ('api:decision-flows:delete', '删除决策流', '/api/v1/decision-flows/*', 'DELETE', 'menu:decision-flows', 3),
    ('api:decision-flows:view',   '查看决策流', '/api/v1/decision-flows', 'GET', 'menu:decision-flows', 4),
    ('api:name-list:manage', '管理名单', '/api/v1/name-list', 'POST', 'menu:name-list', 1),
    ('api:name-list:view',   '查看名单', '/api/v1/name-list', 'GET', 'menu:name-list', 2),
    ('api:name-list:delete', '删除名单', '/api/v1/name-list/*', 'DELETE', 'menu:name-list', 3),
    ('api:feature-catalog:view',   '查看特征字典', '/api/v1/features/catalog', 'GET', 'menu:feature-catalog', 1),
    ('api:feature-catalog:manage', '管理特征字典', '/api/v1/features/catalog', 'POST', 'menu:feature-catalog', 2),
    ('api:grayscale:manage', '管理灰度', '/api/v1/grayscale', 'POST', 'menu:grayscale', 1),
    ('api:grayscale:view',   '查看灰度', '/api/v1/grayscale', 'GET', 'menu:grayscale', 2),
    ('api:system:user:view',   '查看用户', '/api/v1/system/users', 'GET', 'menu:settings', 1),
    ('api:system:user:manage', '管理用户', '/api/v1/system/users', 'POST', 'menu:settings', 2),
    ('api:system:role:view',   '查看角色', '/api/v1/system/roles', 'GET', 'menu:settings', 3),
    ('api:system:role:manage', '管理角色', '/api/v1/system/roles', 'PUT', 'menu:settings', 4)
) AS v(code, name, path, method, menu, ord)
JOIN sys_permission m ON m.permission_code = v.menu;

-- 独立决策执行权限（不挂菜单）
INSERT INTO sys_permission (permission_code, permission_name, resource_type, resource_path, method, sort_order)
VALUES ('api:decision:execute', '执行决策', 'API', '/api/v1/decision/execute', 'POST', 100);

-- 9.4 角色-权限关联
-- 超级管理员：全部权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'SUPER_ADMIN';

-- 管理员：除系统设置菜单与系统管理 API 外的全部权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'ADMIN'
  AND p.permission_code NOT IN (
    'menu:settings',
    'api:system:user:view', 'api:system:user:manage',
    'api:system:role:view', 'api:system:role:manage'
  );

-- 运维人员：规则/决策流/名单/特征/灰度/监控 白名单
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'OPERATOR'
  AND p.permission_code IN (
    'menu:rules', 'menu:decision-flows', 'menu:name-list', 'menu:feature-catalog', 'menu:grayscale', 'menu:monitoring',
    'api:rules:create', 'api:rules:update', 'api:rules:enable', 'api:rules:disable', 'api:rules:view', 'api:rules:validate',
    'api:decision-flows:create', 'api:decision-flows:update', 'api:decision-flows:view',
    'api:name-list:manage', 'api:name-list:view', 'api:name-list:delete',
    'api:feature-catalog:manage', 'api:feature-catalog:view',
    'api:grayscale:manage', 'api:grayscale:view'
  );

-- 只读用户：查看类白名单
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'VIEWER'
  AND p.permission_code IN (
    'menu:rules', 'menu:decision-flows', 'menu:name-list', 'menu:feature-catalog', 'menu:grayscale', 'menu:monitoring', 'menu:analytics',
    'api:rules:view', 'api:decision-flows:view', 'api:name-list:view', 'api:feature-catalog:view', 'api:grayscale:view'
  );

-- 9.5 初始管理员：admin / admin123（BCrypt，上线前必改）
INSERT INTO sys_user (username, password, nickname, status)
VALUES ('admin', '$2a$10$wDw0RTUlW7HP5cxUzXMQBuStit3yQJvxjB3IzOrEFvXIQkqNLXMtK', '系统管理员', 'ACTIVE');

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.role_code = 'SUPER_ADMIN';
