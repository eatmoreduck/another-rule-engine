-- V20__create_sys_team.sql
-- 创建团队和用户-团队关联表，支持数据权限隔离

-- 1. 团队表
CREATE TABLE IF NOT EXISTS sys_team (
    id BIGSERIAL PRIMARY KEY,
    team_code VARCHAR(100) UNIQUE NOT NULL,
    team_name VARCHAR(200) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- 2. 用户-团队关联表
CREATE TABLE IF NOT EXISTS sys_user_team (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    team_id BIGINT NOT NULL REFERENCES sys_team(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_team UNIQUE (user_id, team_id)
);

-- 索引
CREATE INDEX idx_sys_user_team_user_id ON sys_user_team(user_id);
CREATE INDEX idx_sys_user_team_team_id ON sys_user_team(team_id);
