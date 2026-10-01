package com.eatmoreduck.ruleengine.storage.grayscale

import com.eatmoreduck.ruleengine.domain.FeatureCondition
import com.eatmoreduck.ruleengine.domain.FeatureOperator
import com.eatmoreduck.ruleengine.domain.GrayscalePolicy
import com.eatmoreduck.ruleengine.storage.StorageDataCorruptionException
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * 灰度分流策略（领域 sealed [GrayscalePolicy]）与 grayscale_configs 平铺列的双向编解码。
 *
 * 旧模型把策略参数平铺在四列上（strategy_type 选择生效维度）：
 * - strategy_type VARCHAR(30)（DEFAULT 'PERCENTAGE'，可空）
 * - grayscale_percentage INT NOT NULL DEFAULT 0（仅 PERCENTAGE 使用）
 * - feature_rules TEXT（JSON 数组，仅 FEATURE 使用）
 * - whitelist_ids TEXT（逗号分隔，仅 WHITELIST 使用）
 *
 * 兼容性裁决（与旧 CanaryStrategyMatcher 行为对齐的部分）：
 * - strategy_type 为 NULL/未知 → [GrayscalePolicy.Percentage](0)，即"永不命中"（旧 switch default 行为）
 * - WHITELIST 且 whitelist_ids 空白 → [GrayscalePolicy.Percentage](0)（旧"空白名单永不命中"）；
 *   领域 [GrayscalePolicy.Whitelist] 禁止空白名单，故不能原样映射
 * - FEATURE 且 feature_rules 空白 → [GrayscalePolicy.Percentage](0)（旧"空规则不命中"）
 * - feature_rules JSON 损坏 / operator 未知 / 条件集为空 → [StorageDataCorruptionException]（fail fast，
 *   与旧行为的差异点：坏配置必须显式暴露，不允许静默全量失效）
 */
internal object GrayscalePolicyCodec {
    /** 旧列的平铺快照，与 grayscale_configs 的策略相关列一一对应 */
    data class PolicyColumns(
        val strategyType: String,
        val percentage: Int,
        val featureRules: String?,
        val whitelistIds: String?,
    )

    /** feature_rules JSON 的单条条件，字段顺序与旧格式 `{"field","operator","value"}` 一致 */
    internal data class ConditionJson(
        val field: String,
        val operator: String,
        val value: String? = null,
    )

    private val mapper: ObjectMapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .build()

    /** 领域策略 → 平铺列值（写入 grayscale_configs 时使用；未用维度列写 NULL/0，与旧服务的写行为一致） */
    fun encode(policy: GrayscalePolicy): PolicyColumns =
        when (policy) {
            is GrayscalePolicy.Percentage -> {
                PolicyColumns("PERCENTAGE", policy.percentage, null, null)
            }

            is GrayscalePolicy.Whitelist -> {
                PolicyColumns("WHITELIST", 0, null, policy.userIds.sorted().joinToString(separator = ","))
            }

            is GrayscalePolicy.Feature -> {
                PolicyColumns("FEATURE", 0, encodeConditions(policy.conditions), null)
            }
        }

    /** 平铺列值 → 领域策略（读取 grayscale_configs 时使用；可空入参对应 DDL 的可空列） */
    fun decode(
        strategyType: String?,
        percentage: Int,
        featureRules: String?,
        whitelistIds: String?,
    ): GrayscalePolicy =
        when (strategyType?.trim()?.uppercase()) {
            "PERCENTAGE" -> GrayscalePolicy.Percentage(percentage)
            "WHITELIST" -> decodeWhitelist(whitelistIds)
            "FEATURE" -> decodeFeature(featureRules)
            else -> GrayscalePolicy.Percentage(0)
        }

    private fun decodeWhitelist(whitelistIds: String?): GrayscalePolicy {
        val ids =
            whitelistIds
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        if (ids.isEmpty()) {
            // 旧"空白名单永不命中"语义；领域模型把空白名单定义为配置错误，故降级为永不命中的百分比策略
            return GrayscalePolicy.Percentage(0)
        }
        return GrayscalePolicy.Whitelist(ids.toSet())
    }

    private fun decodeFeature(featureRules: String?): GrayscalePolicy {
        if (featureRules.isNullOrBlank()) {
            // 旧"空规则不命中"语义
            return GrayscalePolicy.Percentage(0)
        }
        val conditions =
            try {
                mapper.readValue(featureRules, Array<ConditionJson>::class.java).map { json ->
                    FeatureCondition(
                        field = json.field,
                        operator = decodeOperator(json.operator),
                        value = json.value,
                    )
                }
            } catch (e: JacksonException) {
                throw StorageDataCorruptionException("feature_rules JSON 无法解析: $featureRules", e)
            }
        if (conditions.isEmpty()) {
            throw StorageDataCorruptionException("feature_rules 为空数组（旧匹配器对空条件恒命中，属于配置缺陷，禁止静默保留）")
        }
        return GrayscalePolicy.Feature(conditions)
    }

    private fun decodeOperator(operator: String): FeatureOperator =
        try {
            FeatureOperator.valueOf(operator.trim().uppercase())
        } catch (e: IllegalArgumentException) {
            throw StorageDataCorruptionException("feature_rules 携带未知 operator: $operator", e)
        }

    private fun encodeConditions(conditions: List<FeatureCondition>): String =
        mapper.writeValueAsString(
            conditions.map { condition ->
                ConditionJson(
                    field = condition.field,
                    operator = condition.operator.name,
                    value = condition.value,
                )
            },
        )
}
