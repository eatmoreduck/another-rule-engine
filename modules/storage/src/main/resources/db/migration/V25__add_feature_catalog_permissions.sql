-- V25: add feature catalog menu and API permissions

INSERT INTO sys_permission (permission_code, permission_name, resource_type, resource_path, parent_id, sort_order)
VALUES ('menu:feature-catalog', '特征字典', 'MENU', '/feature-catalog', NULL, 8)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_permission (permission_code, permission_name, resource_type, resource_path, method, parent_id, sort_order)
SELECT 'api:feature-catalog:view', '查看特征字典', 'API', '/api/v1/features/catalog', 'GET', id, 1
FROM sys_permission
WHERE permission_code = 'menu:feature-catalog'
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_permission (permission_code, permission_name, resource_type, resource_path, method, parent_id, sort_order)
SELECT 'api:feature-catalog:manage', '管理特征字典', 'API', '/api/v1/features/catalog', 'POST', id, 2
FROM sys_permission
WHERE permission_code = 'menu:feature-catalog'
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('menu:feature-catalog', 'api:feature-catalog:view', 'api:feature-catalog:manage')
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN', 'OPERATOR')
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('menu:feature-catalog', 'api:feature-catalog:view')
WHERE r.role_code = 'VIEWER'
ON CONFLICT DO NOTHING;
