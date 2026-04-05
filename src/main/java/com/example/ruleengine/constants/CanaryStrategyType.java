package com.example.ruleengine.constants;

/**
 * 灰度策略类型枚举
 * PERCENTAGE - 百分比分流（基于一致性哈希）
 * FEATURE - 特征匹配（基于请求特征条件）
 * WHITELIST - 用户白名单（精确匹配用户ID）
 */
public enum CanaryStrategyType {
    PERCENTAGE("百分比分流"),
    FEATURE("特征匹配"),
    WHITELIST("用户白名单");

    private final String description;

    CanaryStrategyType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
