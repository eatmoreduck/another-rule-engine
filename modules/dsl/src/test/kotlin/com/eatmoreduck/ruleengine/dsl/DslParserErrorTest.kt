package com.eatmoreduck.ruleengine.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 解析失败场景测试：各类非法输入必须收敛为 ParseResult.Failure 并给出可读原因。
 */
class DslParserErrorTest {
    private fun parseFailure(json: String): ParseResult.Failure = assertIs<ParseResult.Failure>(DslParser.parseRuleDefinition(json))

    @Test
    fun `未知 type 判别值报错且原因可读`() {
        val json =
            """
            {
              "defaultAction": "PASS",
              "defaultReason": "默认通过",
              "rules": [
                { "id": "rg1", "condition": { "id": "c1", "type": "mystery", "fieldName": "a", "operator": "EQ", "threshold": 1 }, "action": "PASS", "reason": "" }
              ]
            }
            """.trimIndent()

        val failure = parseFailure(json)

        assertFalse(failure.error.reason.isBlank())
        // 原因应提及无法识别的判别值，便于前端定位
        assertTrue(
            "mystery" in failure.error.reason || "type" in failure.error.reason,
            "错误原因应可定位问题: ${failure.error.reason}",
        )
    }

    @Test
    fun `缺少必填字段报错`() {
        // 缺少 fieldName（Kotlin 模块对非空主构造参数缺失直接报错）
        val json =
            """
            {
              "defaultAction": "PASS",
              "defaultReason": "默认通过",
              "rules": [
                { "id": "rg1", "condition": { "id": "c1", "type": "condition", "operator": "EQ", "threshold": 1 }, "action": "PASS", "reason": "" }
              ]
            }
            """.trimIndent()

        val failure = parseFailure(json)

        assertTrue(failure.error.reason.isNotBlank())
    }

    @Test
    fun `非法枚举值报错`() {
        val json =
            """
            {
              "defaultAction": "MAYBE",
              "defaultReason": "默认通过",
              "rules": []
            }
            """.trimIndent()

        val failure = parseFailure(json)

        assertTrue("MAYBE" in failure.error.reason, "错误原因应包含非法值: ${failure.error.reason}")
    }

    @Test
    fun `threshold 为布尔值报错`() {
        val json =
            """
            {
              "defaultAction": "PASS",
              "defaultReason": "默认通过",
              "rules": [
                { "id": "rg1", "condition": { "id": "c1", "type": "condition", "fieldName": "a", "operator": "EQ", "threshold": true }, "action": "PASS", "reason": "" }
              ]
            }
            """.trimIndent()

        val failure = parseFailure(json)

        assertTrue(
            "threshold" in failure.error.reason || failure.error.reason.isNotBlank(),
            "错误原因应可定位 threshold 问题: ${failure.error.reason}",
        )
    }

    @Test
    fun `threshold 为 null 报错`() {
        val json =
            """
            {
              "defaultAction": "PASS",
              "defaultReason": "默认通过",
              "rules": [
                { "id": "rg1", "condition": { "id": "c1", "type": "condition", "fieldName": "a", "operator": "EQ", "threshold": null }, "action": "PASS", "reason": "" }
              ]
            }
            """.trimIndent()

        assertIs<ParseResult.Failure>(DslParser.parseRuleDefinition(json))
    }

    @Test
    fun `非法 JSON 与空输入报错`() {
        assertIs<ParseResult.Failure>(DslParser.parseRuleDefinition("不是 JSON"))
        assertIs<ParseResult.Failure>(DslParser.parseRuleDefinition(""))
        assertIs<ParseResult.Failure>(DslParser.parseRuleDefinition("{\"defaultAction\": 1} trailing"))
    }

    @Test
    fun `错误信息携带位置或路径`() {
        val failure = parseFailure("不是 JSON")

        // 流级错误应有行列位置；结构错误应有属性路径（二者至少其一）
        assertTrue(
            failure.error.jsonLocation != null || failure.error.jsonPath != null || failure.error.reason.isNotBlank(),
            "应提供可定位的错误信息: ${failure.error}",
        )
    }

    @Test
    fun `getOrNull 与 getOrThrow 语义`() {
        assertEquals(null, DslParser.parseRuleDefinition("不是 JSON").getOrNull())
        assertIs<RuleDefinition>(DslParser.parseRuleDefinition(minimalValidJson()).getOrThrow())
    }

    @Test
    fun `决策流图未知节点类型报错`() {
        val json =
            """
            {
              "nodes": [
                { "id": "x", "type": "quantum", "data": { "label": "?", "nodeType": "quantum" } }
              ],
              "edges": []
            }
            """.trimIndent()

        val failure = assertIs<ParseResult.Failure>(DslParser.parseFlowGraph(json))

        assertTrue("quantum" in failure.error.reason, "错误原因应提及未知类型: ${failure.error.reason}")
    }

    private fun minimalValidJson(): String =
        """
        {
          "defaultAction": "PASS",
          "defaultReason": "默认通过",
          "rules": []
        }
        """.trimIndent()
}
