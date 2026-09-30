package com.example.ruleengine.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 灰度策略命中判断测试：边界值、一致性哈希粘性、白名单与特征条件操作符。
 */
class GrayscalePolicyTest {
    // ---------- 百分比分流 ----------

    @Test
    fun `percentage 0 never matches`() {
        // 0% 边界：任何请求都不命中
        val policy = GrayscalePolicy.Percentage(0)
        assertFalse(policy.matches(mapOf("userId" to "u1")))
        assertFalse(policy.matches(emptyMap()))
    }

    @Test
    fun `percentage 100 always matches`() {
        // 100% 边界：任何请求都命中
        val policy = GrayscalePolicy.Percentage(100)
        assertTrue(policy.matches(mapOf("userId" to "u1")))
        assertTrue(policy.matches(emptyMap()))
    }

    @Test
    fun `bucket boundary falls on the open interval`() {
        // 阈值边界：命中条件为 bucket < percentage（开区间）。
        // 对任意 key，percentage = bucket 时恰不命中，percentage = bucket + 1 时恰命中。
        val userId = "boundary-user"
        val bucket = abs(GrayscalePolicy.extractHashKey(mapOf("userId" to userId)).hashCode() % 100)
        val features = mapOf("userId" to userId)
        assertFalse(GrayscalePolicy.Percentage(bucket).matches(features))
        assertTrue(GrayscalePolicy.Percentage(bucket + 1).matches(features))
    }

    @Test
    fun `same user is sticky across repeated evaluations`() {
        // 一致性哈希粘性：同一 userId 多次求值结果一致（旧实现的核心语义）
        val policy = GrayscalePolicy.Percentage(50)
        val features = mapOf("userId" to "sticky-user", "orderId" to "20260930-001")
        val first = policy.matches(features)
        repeat(20) { assertEquals(first, policy.matches(features)) }
    }

    @Test
    fun `userId takes priority over sessionId for hashing`() {
        // hashKey 优先级：userId 存在时 sessionId 的变化不影响分流结果
        val policy = GrayscalePolicy.Percentage(50)
        val withSessionA = mapOf("userId" to "u1", "sessionId" to "session-A")
        val withSessionB = mapOf("userId" to "u1", "sessionId" to "session-B")
        assertEquals(policy.matches(withSessionA), policy.matches(withSessionB))
    }

    @Test
    fun `empty features fall back to stable deterministic hashing`() {
        // 兜底 hashKey：无 userId/sessionId 时按全部 features 排序拼接，结果确定
        val policy = GrayscalePolicy.Percentage(50)
        val features = mapOf("device" to "ios", "region" to "US")
        assertEquals(policy.matches(features), policy.matches(mapOf("region" to "US", "device" to "ios")))
    }

    @Test
    fun `percentage out of range is rejected`() {
        // 不变式：percentage 落在 0..100
        assertFailsWith<IllegalArgumentException> { GrayscalePolicy.Percentage(-1) }
        assertFailsWith<IllegalArgumentException> { GrayscalePolicy.Percentage(101) }
    }

    // ---------- 用户白名单 ----------

    @Test
    fun `whitelist matches exact userId`() {
        // 精确匹配 userId
        val policy = GrayscalePolicy.Whitelist(setOf("u1", "u2"))
        assertTrue(policy.matches(mapOf("userId" to "u1")))
        assertFalse(policy.matches(mapOf("userId" to "u3")))
    }

    @Test
    fun `whitelist without userId never matches`() {
        // 请求缺少 userId 时不命中（与旧实现一致）
        val policy = GrayscalePolicy.Whitelist(setOf("u1"))
        assertFalse(policy.matches(mapOf("sessionId" to "s1")))
        assertFalse(policy.matches(emptyMap()))
    }

    @Test
    fun `empty or blank whitelist is rejected`() {
        // 空白名单是配置错误：构造即失败，而不是运行期永不命中的静默行为
        assertFailsWith<IllegalArgumentException> { GrayscalePolicy.Whitelist(emptySet()) }
        assertFailsWith<IllegalArgumentException> { GrayscalePolicy.Whitelist(setOf("u1", " ")) }
    }

    // ---------- 特征条件 ----------

    @Test
    fun `string operators behave exactly as legacy evaluateCondition`() {
        // 字符串类操作符与旧 evaluateCondition 一一对应
        fun condition(
            op: FeatureOperator,
            value: String?,
        ) = GrayscalePolicy.Feature(listOf(FeatureCondition("region", op, value)))

        assertTrue(condition(FeatureOperator.EQ, "US").matches(mapOf("region" to "US")))
        assertFalse(condition(FeatureOperator.EQ, "US").matches(mapOf("region" to "CN")))
        assertTrue(condition(FeatureOperator.NE, "US").matches(mapOf("region" to "CN")))
        assertFalse(condition(FeatureOperator.NE, "US").matches(mapOf("region" to "US")))
        assertTrue(condition(FeatureOperator.CONTAINS, "8").matches(mapOf("region" to "ios18")))
        assertFalse(condition(FeatureOperator.CONTAINS, "8").matches(mapOf("region" to "ios17")))
        assertTrue(condition(FeatureOperator.NOT_CONTAINS, "8").matches(mapOf("region" to "ios17")))
        assertFalse(condition(FeatureOperator.NOT_CONTAINS, "8").matches(mapOf("region" to "ios18")))
    }

    @Test
    fun `numeric operators compare numerically and fail closed on unparseable values`() {
        // 数值类操作符：数值比较；任一侧不可解析为数字则不命中（fail closed）
        fun condition(
            op: FeatureOperator,
            value: String?,
        ) = GrayscalePolicy.Feature(listOf(FeatureCondition("age", op, value)))

        assertTrue(condition(FeatureOperator.GT, "18").matches(mapOf("age" to "19")))
        assertFalse(condition(FeatureOperator.GT, "18").matches(mapOf("age" to "18")))
        assertTrue(condition(FeatureOperator.GE, "18").matches(mapOf("age" to "18")))
        assertTrue(condition(FeatureOperator.LT, "18").matches(mapOf("age" to "17.5")))
        assertFalse(condition(FeatureOperator.LT, "18").matches(mapOf("age" to "18")))
        assertTrue(condition(FeatureOperator.LE, "18").matches(mapOf("age" to "18")))
        assertFalse(condition(FeatureOperator.GT, "18").matches(mapOf("age" to "abc")))
        assertFalse(condition(FeatureOperator.GT, "abc").matches(mapOf("age" to "19")))
    }

    @Test
    fun `IN operator splits expected value on commas`() {
        // IN：期望值按逗号分隔后精确匹配，忽略空白项
        val policy = GrayscalePolicy.Feature(listOf(FeatureCondition("region", FeatureOperator.IN, "US, CN , ,JP")))
        assertTrue(policy.matches(mapOf("region" to "CN")))
        assertTrue(policy.matches(mapOf("region" to "JP")))
        assertFalse(policy.matches(mapOf("region" to "UK")))
    }

    @Test
    fun `missing actual value never matches`() {
        // 实际值缺失（null）一律不命中，与旧实现一致
        val policy = GrayscalePolicy.Feature(listOf(FeatureCondition("region", FeatureOperator.EQ, "US")))
        assertFalse(policy.matches(mapOf("other" to "US")))
        assertFalse(policy.matches(emptyMap()))
    }

    @Test
    fun `feature conditions are AND-combined`() {
        // AND 语义：所有条件全部满足才命中
        val policy =
            GrayscalePolicy.Feature(
                listOf(
                    FeatureCondition("region", FeatureOperator.EQ, "US"),
                    FeatureCondition("age", FeatureOperator.GE, "18"),
                ),
            )
        assertTrue(policy.matches(mapOf("region" to "US", "age" to "20")))
        assertFalse(policy.matches(mapOf("region" to "US", "age" to "16")))
        assertFalse(policy.matches(mapOf("region" to "CN", "age" to "20")))
    }

    @Test
    fun `feature policy rejects empty conditions and blank field`() {
        // 空条件集与空白字段都是配置错误
        assertFailsWith<IllegalArgumentException> { GrayscalePolicy.Feature(emptyList()) }
        assertFailsWith<IllegalArgumentException> { FeatureCondition(" ", FeatureOperator.EQ, "US") }
    }

    // ---------- 类型映射 ----------

    @Test
    fun `policy type names map to legacy strategyType strings`() {
        // 与旧 strategyType 字符串一致，保证 API 无损映射
        assertEquals("PERCENTAGE", GrayscalePolicy.Percentage(10).typeName)
        assertEquals("WHITELIST", GrayscalePolicy.Whitelist(setOf("u1")).typeName)
        assertEquals(
            "FEATURE",
            GrayscalePolicy.Feature(listOf(FeatureCondition("f", FeatureOperator.EQ, "v"))).typeName,
        )
    }
}
