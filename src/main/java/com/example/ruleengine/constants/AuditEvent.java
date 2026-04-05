package com.example.ruleengine.constants;

/**
 * 审计事件类型枚举
 */
public enum AuditEvent {
    // 规则操作
    RULE_CREATE("规则创建"),
    RULE_UPDATE("规则更新"),
    RULE_DELETE("规则删除"),
    RULE_ENABLE("规则启用"),
    RULE_DISABLE("规则禁用"),
    RULE_VIEW("规则查看"),

    // 决策流操作
    FLOW_CREATE("决策流创建"),
    FLOW_UPDATE("决策流更新"),
    FLOW_DELETE("决策流删除"),
    FLOW_ENABLE("决策流启用"),
    FLOW_DISABLE("决策流禁用"),

    // 版本操作
    VERSION_CREATE("版本创建"),
    VERSION_ROLLBACK("版本回滚"),
    VERSION_VIEW("版本查看"),
    VERSION_COMPARE("版本比较"),

    // 配置操作
    CONFIG_UPDATE("配置更新"),

    // 用户管理操作
    USER_CREATE("用户创建"),
    USER_UPDATE("用户更新"),
    USER_DISABLE("用户禁用"),
    USER_ENABLE("用户启用"),
    USER_RESET_PASSWORD("用户密码重置"),

    // 角色管理操作
    ROLE_UPDATE_PERMISSIONS("角色权限更新"),

    // 团队管理操作
    TEAM_CREATE("团队创建"),
    TEAM_UPDATE("团队更新"),
    TEAM_DELETE("团队删除"),
    TEAM_ASSIGN_USER("团队分配用户"),
    TEAM_REMOVE_USER("团队移除用户"),

    // 系统操作
    SYSTEM_LOGIN("系统登录"),
    SYSTEM_LOGOUT("系统登出");

    private final String description;

    AuditEvent(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
