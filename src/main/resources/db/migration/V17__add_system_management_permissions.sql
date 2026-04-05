-- V17__add_system_management_permissions.sql
-- 添加系统管理模块的 API 权限码

-- 系统管理 API 权限
INSERT INTO sys_permission (permission_code, permission_name, resource_type, resource_path, method, parent_id, sort_order) VALUES
    ('api:system:user:view', '查看用户', 'API', '/api/v1/system/users', 'GET', 9, 1),
    ('api:system:user:manage', '管理用户', 'API', '/api/v1/system/users', 'POST', 9, 2),
    ('api:system:role:view', '查看角色', 'API', '/api/v1/system/roles', 'GET', 9, 3),
    ('api:system:role:manage', '管理角色', 'API', '/api/v1/system/roles', 'PUT', 9, 4);

-- 超级管理员拥有新权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code IN ('api:system:user:view', 'api:system:user:manage', 'api:system:role:view', 'api:system:role:manage');
