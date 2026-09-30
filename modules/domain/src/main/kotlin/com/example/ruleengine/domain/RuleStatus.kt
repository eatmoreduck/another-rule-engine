package com.example.ruleengine.domain

/**
 * 规则生命周期状态。
 *
 * 阶段 0 冒烟占位：仅包含确定不变的三个核心状态，
 * 完整领域模型（规则 / 版本 / 灰度策略）在阶段 1 按事件溯源思路重新设计。
 */
enum class RuleStatus {
    DRAFT,
    PUBLISHED,
    OFFLINE,
}
