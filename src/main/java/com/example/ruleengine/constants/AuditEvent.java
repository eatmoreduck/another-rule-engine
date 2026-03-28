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

    // 版本操作
    VERSION_CREATE("版本创建"),
    VERSION_ROLLBACK("版本回滚"),
    VERSION_VIEW("版本查看"),
    VERSION_COMPARE("版本比较"),

    // 配置操作
    CONFIG_UPDATE("配置更新"),

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
