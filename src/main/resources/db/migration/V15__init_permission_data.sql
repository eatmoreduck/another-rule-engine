-- V15__init_permission_data.sql
-- 初始化角色、权限和管理员用户数据

-- ============================================================
-- 1. 初始化角色
-- ============================================================
INSERT INTO sys_role (role_code, role_name, description) VALUES
    ('SUPER_ADMIN', '超级管理员', '拥有系统全部权限'),
    ('ADMIN', '管理员', '拥有大部分管理权限，不含系统设置'),
    ('OPERATOR', '运维人员', '规则管理、灰度发布等运维操作'),
    ('VIEWER', '只读用户', '仅查看权限，不可修改');

-- ============================================================
-- 2. 初始化权限（菜单 + API）
-- ============================================================

-- 菜单级权限
INSERT INTO sys_permission (permission_code, permission_name, resource_type, resource_path, parent_id, sort_order) VALUES
    ('menu:rules', '规则配置', 'MENU', '/rules', NULL, 1),
    ('menu:decision-flows', '决策流', 'MENU', '/decision-flows', NULL, 2),
    ('menu:name-list', '名单管理', 'MENU', '/name-list', NULL, 3),
    ('menu:grayscale', '灰度发布', 'MENU', '/grayscale', NULL, 4),
    ('menu:environments', '多环境', 'MENU', '/environments', NULL, 5),
    ('menu:import-export', '导入导出', 'MENU', '/import-export', NULL, 6),
    ('menu:monitoring', '监控仪表盘', 'MENU', '/monitoring', NULL, 7),
    ('menu:analytics', '分析中心', 'MENU', '/analytics', NULL, 8),
    ('menu:settings', '系统设置', 'MENU', '/settings', NULL, 9);

-- 规则管理 API 权限
INSERT INTO sys_permission (permission_code, permission_name, resource_type, resource_path, method, parent_id, sort_order) VALUES
    ('api:rules:create', '创建规则', 'API', '/api/v1/rules', 'POST', 1, 1),
    ('api:rules:update', '更新规则', 'API', '/api/v1/rules/*', 'PUT', 1, 2),
    ('api:rules:delete', '删除规则', 'API', '/api/v1/rules/*', 'DELETE', 1, 3),
    ('api:rules:enable', '启用规则', 'API', '/api/v1/rules/*/enable', 'POST', 1, 4),
    ('api:rules:disable', '禁用规则', 'API', '/api/v1/rules/*/disable', 'POST', 1, 5),
    ('api:rules:view', '查看规则', 'API', '/api/v1/rules', 'GET', 1, 6),
    ('api:rules:validate', '验证脚本', 'API', '/api/v1/rules/validate', 'POST', 1, 7);

-- 决策流 API 权限
INSERT INTO sys_permission (permission_code, permission_name, resource_type, resource_path, method, parent_id, sort_order) VALUES
    ('api:decision-flows:create', '创建决策流', 'API', '/api/v1/decision-flows', 'POST', 2, 1),
    ('api:decision-flows:update', '更新决策流', 'API', '/api/v1/decision-flows/*', 'PUT', 2, 2),
    ('api:decision-flows:delete', '删除决策流', 'API', '/api/v1/decision-flows/*', 'DELETE', 2, 3),
    ('api:decision-flows:view', '查看决策流', 'API', '/api/v1/decision-flows', 'GET', 2, 4);

-- 名单管理 API 权限
INSERT INTO sys_permission (permission_code, permission_name, resource_type, resource_path, method, parent_id, sort_order) VALUES
    ('api:name-list:manage', '管理名单', 'API', '/api/v1/name-list', 'POST', 3, 1),
    ('api:name-list:view', '查看名单', 'API', '/api/v1/name-list', 'GET', 3, 2),
    ('api:name-list:delete', '删除名单', 'API', '/api/v1/name-list/*', 'DELETE', 3, 3);

-- 灰度发布 API 权限
INSERT INTO sys_permission (permission_code, permission_name, resource_type, resource_path, method, parent_id, sort_order) VALUES
    ('api:grayscale:manage', '管理灰度', 'API', '/api/v1/grayscale', 'POST', 4, 1),
    ('api:grayscale:view', '查看灰度', 'API', '/api/v1/grayscale', 'GET', 4, 2);

-- 决策 API 权限
INSERT INTO sys_permission (permission_code, permission_name, resource_type, resource_path, method, parent_id, sort_order) VALUES
    ('api:decision:execute', '执行决策', 'API', '/api/v1/decision/execute', 'POST', NULL, 100);

-- ============================================================
-- 3. 角色-权限关联
-- ============================================================

-- 超级管理员：拥有全部权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'SUPER_ADMIN';

-- 管理员：除系统设置外的所有权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'ADMIN' AND p.permission_code != 'menu:settings';

-- 运维人员：规则管理、灰度发布、监控
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'OPERATOR'
  AND p.permission_code IN (
    'menu:rules', 'menu:decision-flows', 'menu:name-list', 'menu:grayscale', 'menu:monitoring',
    'api:rules:create', 'api:rules:update', 'api:rules:enable', 'api:rules:disable', 'api:rules:view', 'api:rules:validate',
    'api:decision-flows:create', 'api:decision-flows:update', 'api:decision-flows:view',
    'api:name-list:manage', 'api:name-list:view', 'api:name-list:delete',
    'api:grayscale:manage', 'api:grayscale:view'
  );

-- 只读用户：仅查看权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'VIEWER'
  AND p.permission_code IN (
    'menu:rules', 'menu:decision-flows', 'menu:name-list', 'menu:grayscale', 'menu:monitoring', 'menu:analytics',
    'api:rules:view', 'api:decision-flows:view', 'api:name-list:view', 'api:grayscale:view'
  );

-- ============================================================
-- 4. 初始管理员用户
--    密码: admin123 (BCrypt 加密)
--    BCrypt hash generated with strength 10
-- ============================================================
INSERT INTO sys_user (username, password, nickname, status) VALUES
    ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 'ACTIVE');

-- 管理员用户关联超级管理员角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.role_code = 'SUPER_ADMIN';
