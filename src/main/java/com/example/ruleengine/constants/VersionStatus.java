package com.example.ruleengine.constants;

/**
 * 版本状态枚举
 * DRAFT - 草稿（新建版本初始状态，未发布）
 * CANARY - 灰度中（正在灰度验证）
 * ACTIVE - 生效中（当前生效的版本）
 * ARCHIVED - 已归档（被新版本替代的历史版本）
 */
public enum VersionStatus {
    DRAFT("草稿"),
    CANARY("灰度中"),
    ACTIVE("生效中"),
    ARCHIVED("已归档");

    private final String description;

    VersionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
