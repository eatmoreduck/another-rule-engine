package com.eatmoreduck.ruleengine.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 决策动作与决策结果值对象测试。
 */
class DecisionResultTest {
    // ---------- 决策动作全集 ----------

    @Test
    fun `decision action set is pass manual-review reject`() {
        // 全集三态：放行 / 人工审核 / 拒绝（与产品核心语义一致）
        assertEquals(3, DecisionAction.entries.size)
        assertEquals("PASS", DecisionAction.PASS.codeName)
        assertEquals("MANUAL_REVIEW", DecisionAction.MANUAL_REVIEW.codeName)
        assertEquals("REJECT", DecisionAction.REJECT.codeName)
    }

    @Test
    fun `engine output parsing falls back to REJECT`() {
        // 旧引擎 decision 字符串解析：识别大小写变体，无法识别时 fail-safe 归为拒绝
        assertEquals(DecisionAction.PASS, DecisionAction.fromEngineOutput("PASS"))
        assertEquals(DecisionAction.PASS, DecisionAction.fromEngineOutput("pass"))
        assertEquals(DecisionAction.MANUAL_REVIEW, DecisionAction.fromEngineOutput("MANUAL_REVIEW"))
        assertEquals(DecisionAction.REJECT, DecisionAction.fromEngineOutput("REJECT"))
        assertEquals(DecisionAction.REJECT, DecisionAction.fromEngineOutput("whatever"))
        assertEquals(DecisionAction.REJECT, DecisionAction.fromEngineOutput(null))
        assertEquals(DecisionAction.REJECT, DecisionAction.fromEngineOutput(" "))
    }

    // ---------- 工厂函数 ----------

    @Test
    fun `factories produce actions with metadata`() {
        // 三个动作工厂与超时兜底工厂
        val passed = DecisionResult.passed(reason = "无风险", executionTimeMs = 12L)
        val reviewed = DecisionResult.manualReview(reason = "命中可疑规则", executionTimeMs = 20L)
        val rejected = DecisionResult.rejected(reason = "命中黑名单", executionTimeMs = 8L)

        assertEquals(DecisionAction.PASS, passed.action)
        assertEquals(DecisionAction.MANUAL_REVIEW, reviewed.action)
        assertEquals(DecisionAction.REJECT, rejected.action)
        assertFalse(passed.timedOut)
    }

    @Test
    fun `timeout result is a REJECT with timedOut flag`() {
        // 超时兜底：动作固定 REJECT（fail-safe），携带 timedOut 标记
        val timeout = DecisionResult.timedOut(elapsedMs = 50L, reason = "决策超时")
        assertEquals(DecisionAction.REJECT, timeout.action)
        assertTrue(timeout.timedOut)
        assertEquals(50L, timeout.executionTimeMs)
    }

    // ---------- 不变式失败路径 ----------

    @Test
    fun `negative executionTimeMs is rejected`() {
        // 耗时不可能为负
        assertFailsWith<IllegalArgumentException> {
            DecisionResult(action = DecisionAction.PASS, executionTimeMs = -1L)
        }
    }

    @Test
    fun `timedOut with non-REJECT action is rejected`() {
        // 超时兜底语义不允许与放行/人工审核组合
        assertFailsWith<IllegalStateException> {
            DecisionResult(action = DecisionAction.PASS, executionTimeMs = 5L, timedOut = true)
        }
    }

    // ---------- 值语义 ----------

    @Test
    fun `results with same fields are equal`() {
        // data class 等价性
        assertEquals(
            DecisionResult.rejected(reason = "r", executionTimeMs = 1L),
            DecisionResult.rejected(reason = "r", executionTimeMs = 1L),
        )
    }

    @Test
    fun `reason defaults to null`() {
        // 无理由的决策合法（旧模型 reason 可空）
        assertNull(DecisionResult.passed(executionTimeMs = 1L).reason)
    }
}
