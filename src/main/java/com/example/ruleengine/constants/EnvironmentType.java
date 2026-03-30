package com.example.ruleengine.constants;

/**
 * 环境类型枚举
 */
public enum EnvironmentType {
    DEV("开发环境"),
    STAGING("预发布环境"),
    PRODUCTION("生产环境");

    private final String description;

    EnvironmentType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
