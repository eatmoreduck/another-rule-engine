package com.eatmoreduck.ruleengine.storage

import com.eatmoreduck.ruleengine.domain.FeatureCondition
import com.eatmoreduck.ruleengine.domain.FeatureOperator
import com.eatmoreduck.ruleengine.domain.GrayscalePolicy
import com.eatmoreduck.ruleengine.storage.grayscale.GrayscalePolicyCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 灰度策略 sealed 模型 ↔ grayscale_configs 平铺列的编解码单元测试（无数据库）。
 *
 * 重点：三种策略的平铺往返、与旧 CanaryStrategyMatcher 数据格式的兼容、
 * 坏数据/旧合法数据的降级裁决。
 */
class GrayscalePolicyCodecTest {
    // ---------- encode：领域 → 平铺列 ----------

    @Test
    @DisplayName("Percentage 策略编码：strategyType=PERCENTAGE，未用维度列为 NULL")
    fun encodePercentage() {
        val columns = GrayscalePolicyCodec.encode(GrayscalePolicy.Percentage(30))
        assertEquals("PERCENTAGE", columns.strategyType)
        assertEquals(30, columns.percentage)
        assertNull(columns.featureRules)
        assertNull(columns.whitelistIds)
    }

    @Test
    @DisplayName("Whitelist 策略编码：逗号分隔且有序（确定性写入），percentage 归 0")
    fun encodeWhitelist() {
        val columns = GrayscalePolicyCodec.encode(GrayscalePolicy.Whitelist(setOf("u2", "u1")))
        assertEquals("WHITELIST", columns.strategyType)
        assertEquals("u1,u2", columns.whitelistIds)
        assertEquals(0, columns.percentage)
        assertNull(columns.featureRules)
    }

    @Test
    @DisplayName("Feature 策略编码：conditions 序列化为旧格式 JSON 数组")
    fun encodeFeature() {
        val policy =
            GrayscalePolicy.Feature(
                listOf(
                    FeatureCondition("region", FeatureOperator.EQ, "US"),
                    FeatureCondition("order_amount", FeatureOperator.GT, "100"),
                    FeatureCondition("coupon", FeatureOperator.NE, null),
                ),
            )
        val columns = GrayscalePolicyCodec.encode(policy)
        assertEquals("FEATURE", columns.strategyType)
        // 与旧 featureRules 格式逐字段一致：{"field","operator","value"}
        assertEquals(
            """[{"field":"region","operator":"EQ","value":"US"},{"field":"order_amount","operator":"GT","value":"100"},{"field":"coupon","operator":"NE","value":null}]""",
            columns.featureRules,
        )
        assertEquals(0, columns.percentage)
        assertNull(columns.whitelistIds)
    }

    // ---------- decode：平铺列 → 领域 ----------

    @Test
    @DisplayName("PERCENTAGE 平铺列解码回 Percentage 策略")
    fun decodePercentage() {
        val policy = GrayscalePolicyCodec.decode("PERCENTAGE", 45, null, null)
        assertEquals(GrayscalePolicy.Percentage(45), policy)
    }

    @Test
    @DisplayName("WHITELIST 平铺列解码：逗号分隔还原为集合，容忍空白条目")
    fun decodeWhitelist() {
        val policy = GrayscalePolicyCodec.decode("WHITELIST", 0, null, "u1, u2 ,,u3")
        assertEquals(GrayscalePolicy.Whitelist(setOf("u1", "u2", "u3")), policy)
    }

    @Test
    @DisplayName("FEATURE 平铺列解码：手工构造的旧格式 JSON 可解析（向前兼容）")
    fun decodeLegacyFeatureRulesJson() {
        // 旧后端写入的典型格式（旧 ObjectMapper 产出，字段顺序/空格可能不同）
        val legacyJson = """[{"field":"region","operator":"EQ","value":"US"},{"field":"age","operator":"GT","value":"18"}]"""
        val policy = GrayscalePolicyCodec.decode("FEATURE", 0, legacyJson, null)
        assertEquals(
            GrayscalePolicy.Feature(
                listOf(
                    FeatureCondition("region", FeatureOperator.EQ, "US"),
                    FeatureCondition("age", FeatureOperator.GT, "18"),
                ),
            ),
            policy,
        )
    }

    @Test
    @DisplayName("未知/空 strategyType 降级为 Percentage(0)（旧 switch default：永不命中）")
    fun decodeUnknownStrategyTypeFallsBackToNeverMatch() {
        assertEquals(GrayscalePolicy.Percentage(0), GrayscalePolicyCodec.decode(null, 0, null, null))
        assertEquals(GrayscalePolicy.Percentage(0), GrayscalePolicyCodec.decode("SOMETHING_NEW", 50, null, null))
    }

    @Test
    @DisplayName("空白名单（旧合法数据）降级为 Percentage(0)，不触发领域不变式异常")
    fun decodeEmptyWhitelistFallsBackToNeverMatch() {
        assertEquals(GrayscalePolicy.Percentage(0), GrayscalePolicyCodec.decode("WHITELIST", 0, null, null))
        assertEquals(GrayscalePolicy.Percentage(0), GrayscalePolicyCodec.decode("WHITELIST", 0, null, " , ,"))
    }

    @Test
    @DisplayName("空 feature_rules（旧合法数据）降级为 Percentage(0)")
    fun decodeBlankFeatureRulesFallsBackToNeverMatch() {
        assertEquals(GrayscalePolicy.Percentage(0), GrayscalePolicyCodec.decode("FEATURE", 0, null, null))
        assertEquals(GrayscalePolicy.Percentage(0), GrayscalePolicyCodec.decode("FEATURE", 0, "  ", null))
    }

    @Test
    @DisplayName("损坏的 feature_rules JSON fail fast，抛 StorageDataCorruptionException")
    fun decodeCorruptedFeatureRulesJsonFailsFast() {
        val ex =
            assertThrows(
                StorageDataCorruptionException::class.java,
            ) { GrayscalePolicyCodec.decode("FEATURE", 0, "[{field:broken", null) }
        assertTrue(ex.message!!.contains("feature_rules"))
    }

    @Test
    @DisplayName("空条件数组 fail fast（旧匹配器对空条件恒命中，属于配置缺陷）")
    fun decodeEmptyConditionArrayFailsFast() {
        assertThrows(
            StorageDataCorruptionException::class.java,
        ) { GrayscalePolicyCodec.decode("FEATURE", 0, "[]", null) }
    }

    @Test
    @DisplayName("未知 operator fail fast")
    fun decodeUnknownOperatorFailsFast() {
        val json = """[{"field":"region","operator":"REGEX","value":"US"}]"""
        assertThrows(
            StorageDataCorruptionException::class.java,
        ) { GrayscalePolicyCodec.decode("FEATURE", 0, json, null) }
    }

    // ---------- 往返 ----------

    @Test
    @DisplayName("三种策略 encode → decode 往返等价")
    fun roundTripAllStrategies() {
        val policies =
            listOf(
                GrayscalePolicy.Percentage(0),
                GrayscalePolicy.Percentage(100),
                GrayscalePolicy.Percentage(37),
                GrayscalePolicy.Whitelist(setOf("user-a", "user-b")),
                GrayscalePolicy.Feature(listOf(FeatureCondition("risk_score", FeatureOperator.GE, "0.8"))),
            )
        policies.forEach { original ->
            val columns = GrayscalePolicyCodec.encode(original)
            val decoded =
                GrayscalePolicyCodec.decode(
                    strategyType = columns.strategyType,
                    percentage = columns.percentage,
                    featureRules = columns.featureRules,
                    whitelistIds = columns.whitelistIds,
                )
            assertEquals(original, decoded, "策略往返失真: $original")
        }
    }
}
