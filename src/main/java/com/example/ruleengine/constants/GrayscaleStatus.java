package com.example.ruleengine.constants;

/**
 * 灰度发布状态枚举
 * DRAFT - 草稿
 * RUNNING - 运行中
 * PAUSED - 已暂停
 * COMPLETED - 已完成（全量切换）
 * ROLLED_BACK - 已回滚
 */
public enum GrayscaleStatus {
    DRAFT("草稿"),
    RUNNING("运行中"),
    PAUSED("已暂停"),
    COMPLETED("已完成"),
    ROLLED_BACK("已回滚");

    private final String description;

    GrayscaleStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
