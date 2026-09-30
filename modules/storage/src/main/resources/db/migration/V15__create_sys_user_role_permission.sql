-- V14__create_sys_user_role_permission.sql
-- 创建用户、角色、权限相关表结构

-- 1. 用户表
CREATE TABLE IF NOT EXISTS sys_user (
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

-- 2. 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(100) UNIQUE NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_sys_role_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- 3. 权限表
CREATE TABLE IF NOT EXISTS sys_permission (
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

-- 4. 用户-角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT uq_sys_user_role UNIQUE (user_id, role_id)
);

-- 5. 角色-权限关联表
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sys_role_perm_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_sys_role_perm_perm FOREIGN KEY (permission_id) REFERENCES sys_permission(id) ON DELETE CASCADE,
    CONSTRAINT uq_sys_role_perm UNIQUE (role_id, permission_id)
);

-- 索引
CREATE INDEX idx_sys_user_username ON sys_user(username);
CREATE INDEX idx_sys_user_status ON sys_user(status);
CREATE INDEX idx_sys_role_code ON sys_role(role_code);
CREATE INDEX idx_sys_perm_code ON sys_permission(permission_code);
CREATE INDEX idx_sys_perm_parent ON sys_permission(parent_id);
CREATE INDEX idx_sys_user_role_user ON sys_user_role(user_id);
CREATE INDEX idx_sys_user_role_role ON sys_user_role(role_id);
CREATE INDEX idx_sys_role_perm_role ON sys_role_permission(role_id);
CREATE INDEX idx_sys_role_perm_perm ON sys_role_permission(permission_id);

-- 注释
COMMENT ON TABLE sys_user IS '系统用户表';
COMMENT ON COLUMN sys_user.username IS '用户名，唯一';
COMMENT ON COLUMN sys_user.password IS '密码（BCrypt 加密）';
COMMENT ON COLUMN sys_user.status IS '状态: ACTIVE-正常, DISABLED-禁用, LOCKED-锁定';

COMMENT ON TABLE sys_role IS '系统角色表';
COMMENT ON COLUMN sys_role.role_code IS '角色编码，唯一';
COMMENT ON COLUMN sys_role.role_name IS '角色名称';

COMMENT ON TABLE sys_permission IS '系统权限表';
COMMENT ON COLUMN sys_permission.permission_code IS '权限编码，唯一';
COMMENT ON COLUMN sys_permission.resource_type IS '资源类型: MENU-菜单, BUTTON-按钮, API-接口';
COMMENT ON COLUMN sys_permission.resource_path IS '资源路径（API路径或菜单路由）';

COMMENT ON TABLE sys_user_role IS '用户-角色关联表';
COMMENT ON TABLE sys_role_permission IS '角色-权限关联表';
