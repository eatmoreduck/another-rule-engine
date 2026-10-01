package com.eatmoreduck.ruleengine.domain

import kotlin.math.abs

/**
 * 灰度分流策略（不可变值对象 + 命中判断纯函数）。
 *
 * 从旧 CanaryStrategyType / CanaryStrategyMatcher / GrayscaleConfig 提取三种维度：
 * - [Percentage]：百分比分流，基于一致性哈希，同一用户/会话多次请求结果稳定
 * - [Whitelist]：用户白名单，精确匹配 features 中的 userId
 * - [Feature]：特征条件匹配，全部条件满足（AND 语义）才命中
 *
 * 旧模型把 percentage / featureRules / whitelistIds 三个字段平铺在 GrayscaleConfig 上、
 * 由 strategyType 字符串选择生效字段；本设计将各维度的参数内聚到对应策略值对象，
 * 消除"字段与策略类型不匹配"这类非法组合。
 */
sealed interface GrayscalePolicy {
    /** 对应旧 strategyType 字符串，便于 API 无损映射 */
    val typeName: String

    /**
     * 判断请求特征是否命中灰度。
     *
     * @param features 请求特征数据（含 userId、sessionId 等）
     * @return true 表示命中灰度，应使用灰度版本
     */
    fun matches(features: Map<String, Any?>): Boolean

    /**
     * 百分比分流策略。
     *
     * 命中算法与旧 CanaryStrategyMatcher.matchByPercentage 完全一致：
     * hashValue = abs(hashKey.hashCode() % 100)；命中当 hashValue < percentage。
     * hashKey 优先级：userId > sessionId > 全部 features 按 key 排序后拼接。
     *
     * 不变式：percentage 落在 0..100。
     */
    data class Percentage(
        val percentage: Int,
    ) : GrayscalePolicy {
        init {
            require(percentage in 0..100) { "percentage 必须落在 0..100，实际: $percentage" }
        }

        override val typeName: String
            get() = "PERCENTAGE"

        override fun matches(features: Map<String, Any?>): Boolean {
            if (percentage <= 0) return false
            if (percentage >= 100) return true
            val hashValue = abs(extractHashKey(features).hashCode() % 100)
            return hashValue < percentage
        }
    }

    /**
     * 用户白名单策略。
     *
     * 旧实现将逗号分隔字符串解析为列表后 contains；此处建模为集合，
     * 语义不变：features.userId 精确命中集合内任一 ID 即命中。
     *
     * 不变式：userIds 非空且无空白条目（空白名单是配置错误，fail fast，
     * 与旧行为"空白名单永不命中"不同，见设计取舍）。
     */
    data class Whitelist(
        val userIds: Set<String>,
    ) : GrayscalePolicy {
        init {
            require(userIds.isNotEmpty()) { "白名单不能为空（空白名单属于配置错误，应直接不创建灰度）" }
            require(userIds.all { it.isNotBlank() }) { "白名单不允许空白条目" }
        }

        override val typeName: String
            get() = "WHITELIST"

        override fun matches(features: Map<String, Any?>): Boolean {
            val userId = features[HASH_KEY_USER_ID] ?: return false
            return userId.toString() in userIds
        }
    }

    /**
     * 特征条件策略。
     *
     * 对应旧 featureRules JSON 数组（[{"field":"region","operator":"EQ","value":"US"}, ...]），
     * 所有条件全部满足（AND 语义）才命中。
     *
     * 不变式：conditions 非空（空条件集是配置错误，fail fast）。
     */
    data class Feature(
        val conditions: List<FeatureCondition>,
    ) : GrayscalePolicy {
        init {
            require(conditions.isNotEmpty()) { "特征条件不能为空（空条件集属于配置错误）" }
        }

        override val typeName: String
            get() = "FEATURE"

        override fun matches(features: Map<String, Any?>): Boolean = conditions.all { it.evaluate(features[it.field]) }
    }

    companion object {
        const val HASH_KEY_USER_ID: String = "userId"
        const val HASH_KEY_SESSION_ID: String = "sessionId"

        /**
         * 提取一致性哈希的 key，与旧 CanaryStrategyMatcher.extractHashKey 一致：
         * 优先 userId，其次 sessionId，兜底使用全部 features 按 key 排序后拼接。
         */
        internal fun extractHashKey(features: Map<String, Any?>): String {
            val userId = features[HASH_KEY_USER_ID]
            if (userId != null && userId.toString().isNotEmpty()) return userId.toString()
            val sessionId = features[HASH_KEY_SESSION_ID]
            if (sessionId != null && sessionId.toString().isNotEmpty()) return sessionId.toString()
            return features.entries
                .sortedBy { it.key }
                .joinToString(separator = "&") { "${it.key}=${it.value}" }
        }
    }
}

/**
 * 特征条件操作符。
 *
 * 与旧 CanaryStrategyMatcher.evaluateCondition 支持的操作符一一对应，
 * codeName 即旧 JSON 中的 operator 字符串。
 */
enum class FeatureOperator {
    EQ,
    NE,
    CONTAINS,
    NOT_CONTAINS,
    GT,
    GE,
    LT,
    LE,
    IN,
}

/**
 * 单条特征条件值对象。
 *
 * 求值语义与旧 evaluateCondition 完全一致：
 * - 实际值缺失（null）一律不命中
 * - EQ/NE/CONTAINS/NOT_CONTAINS 走字符串比较
 * - GT/GE/LT/LE 走数值比较，任一侧无法解析为数字则不命中（不抛异常）
 * - IN 将期望值按逗号分隔为列表后精确匹配（忽略空白项）
 */
data class FeatureCondition(
    val field: String,
    val operator: FeatureOperator,
    val value: String? = null,
) {
    init {
        require(field.isNotBlank()) { "特征条件 field 不能为空白" }
    }

    /**
     * 对实际特征值求值。
     *
     * @param actual 请求特征的实际值
     * @return 条件是否满足
     */
    fun evaluate(actual: Any?): Boolean {
        if (actual == null) return false
        val actualText = actual.toString()
        val expectedText = value ?: ""
        return when (operator) {
            FeatureOperator.EQ -> actualText == expectedText
            FeatureOperator.NE -> actualText != expectedText
            FeatureOperator.CONTAINS -> actualText.contains(expectedText)
            FeatureOperator.NOT_CONTAINS -> !actualText.contains(expectedText)
            FeatureOperator.GT -> compareNumeric(actualText, expectedText) { a, e -> a > e }
            FeatureOperator.GE -> compareNumeric(actualText, expectedText) { a, e -> a >= e }
            FeatureOperator.LT -> compareNumeric(actualText, expectedText) { a, e -> a < e }
            FeatureOperator.LE -> compareNumeric(actualText, expectedText) { a, e -> a <= e }
            FeatureOperator.IN -> splitExpectedList(expectedText).contains(actualText)
        }
    }

    /** 数值比较：任一侧无法解析为数字则不命中（与旧行为一致，不抛异常） */
    private fun compareNumeric(
        actual: String,
        expected: String,
        compare: (Double, Double) -> Boolean,
    ): Boolean {
        val actualNumber = actual.toDoubleOrNull() ?: return false
        val expectedNumber = expected.toDoubleOrNull() ?: return false
        return compare(actualNumber, expectedNumber)
    }

    private fun splitExpectedList(expected: String): List<String> = expected.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
