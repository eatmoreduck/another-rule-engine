package com.example.ruleengine.constants;

/**
 * 决策流状态枚举
 * DRAFT - 草稿
 * ACTIVE - 生效中
 * ARCHIVED - 已归档
 * DELETED - 已删除
 */
public enum DecisionFlowStatus {
    DRAFT("草稿"),
    ACTIVE("生效中"),
    ARCHIVED("已归档"),
    DELETED("已删除");

    private final String description;

    DecisionFlowStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
