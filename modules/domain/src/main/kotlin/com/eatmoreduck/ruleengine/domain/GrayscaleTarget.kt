package com.eatmoreduck.ruleengine.domain

/**
 * 灰度目标类型。
 *
 * 从旧 GrayscaleConfig.targetType / CanaryExecutionLog.targetType 提取：
 * 灰度发布不仅作用于单条规则，也作用于整条决策流。
 */
enum class GrayscaleTargetType {
    RULE,
    DECISION_FLOW,
    ;

    /** 对应旧 targetType 字符串，便于 API 无损映射 */
    val codeName: String
        get() = name
}

/**
 * 灰度目标值对象：目标类型 + 目标业务键（规则 Key 或决策流 Key）。
 *
 * 不变式：key 非空白。
 */
data class GrayscaleTarget(
    val type: GrayscaleTargetType,
    val key: String,
) {
    init {
        require(key.isNotBlank()) { "灰度目标 key 不能为空白" }
    }
}
