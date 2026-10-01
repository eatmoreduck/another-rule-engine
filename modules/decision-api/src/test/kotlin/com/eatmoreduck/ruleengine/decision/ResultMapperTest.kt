package com.eatmoreduck.ruleengine.decision

import com.eatmoreduck.ruleengine.decision.core.ResultMapper
import com.eatmoreduck.ruleengine.domain.DecisionAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 规则输出映射单测：fail-safe 语义（无法识别/异常输出归 REJECT）与旧 buildDecisionResponse 对齐。
 */
class ResultMapperTest {
    private val context = mapOf<String, Any?>("order_amount" to 1500.0)

    @Test
    fun `boolean true maps to PASS`() {
        val result = ResultMapper.toDecision(true, context)
        assertEquals(DecisionAction.PASS, result.action)
        assertEquals("规则执行完成", result.reason)
    }

    @Test
    fun `boolean false maps to REJECT`() {
        val result = ResultMapper.toDecision(false, context)
        assertEquals(DecisionAction.REJECT, result.action)
    }

    @Test
    fun `map payload carries decision and reason fields`() {
        val result = ResultMapper.toDecision(mapOf("decision" to "MANUAL_REVIEW", "reason" to "疑似欺诈"), context)
        assertEquals(DecisionAction.MANUAL_REVIEW, result.action)
        assertEquals("疑似欺诈", result.reason)
    }

    @Test
    fun `map without decision falls back to REJECT`() {
        val result = ResultMapper.toDecision(mapOf<String, Any?>("reason" to "无决策字段"), context)
        assertEquals(DecisionAction.REJECT, result.action)
        assertEquals("无决策字段", result.reason)
    }

    @Test
    fun `string passthrough normalizes decision`() {
        assertEquals(DecisionAction.PASS, ResultMapper.toDecision("PASS", context).action)
        assertEquals(DecisionAction.REJECT, ResultMapper.toDecision("REJECT", context).action)
        assertEquals(DecisionAction.MANUAL_REVIEW, ResultMapper.toDecision("manual_review", context).action)
    }

    @Test
    fun `unrecognized string fails safe to REJECT`() {
        assertEquals(DecisionAction.REJECT, ResultMapper.toDecision("MAYBE", context).action)
    }

    @Test
    fun `null and arbitrary outputs fail safe to REJECT with reason`() {
        val nullResult = ResultMapper.toDecision(null, context)
        assertEquals(DecisionAction.REJECT, nullResult.action)
        assertTrue(nullResult.reason!!.contains("无效结果"))

        val numberResult = ResultMapper.toDecision(42, context)
        assertEquals(DecisionAction.REJECT, numberResult.action)
        assertTrue(numberResult.reason!!.contains("42"))
    }

    @Test
    fun `execution context is echoed`() {
        val result = ResultMapper.toDecision("PASS", context)
        assertEquals(context, result.executionContext)
    }
}
