package com.example.ruleengine.constants;

/**
 * 规则状态枚举
 * DRAFT - 草稿
 * ACTIVE - 生效中
 * ARCHIVED - 已归档
 * DELETED - 已删除
 */
public enum RuleStatus {
    DRAFT("草稿"),
    ACTIVE("生效中"),
    ARCHIVED("已归档"),
    DELETED("已删除");

    private final String description;

    RuleStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
