package com.example.ruleengine.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 规则定义树 JSON 序列化/反序列化测试。
 *
 * 样例按前端 `frontend/src/types/ruleConfig.ts` 的 FormRuleConfigV2 形状手工构造，
 * 字段名与层级逐字对齐（前端 JSON 是事实标准）。
 */
class RuleDefinitionJsonTest {
    /** 贴近前端真实形状的多层嵌套样例：OR 根组 → 原子条件 + AND 子组（再嵌原子条件） */
    private val nestedRuleJson =
        """
        {
          "defaultAction": "PASS",
          "defaultReason": "默认通过",
          "rules": [
            {
              "id": "rg1",
              "condition": {
                "id": "lg1",
                "type": "group",
                "logic": "OR",
                "children": [
                  {
                    "id": "c1",
                    "type": "condition",
                    "fieldName": "amount",
                    "operator": "GT",
                    "threshold": 10000
                  },
                  {
                    "id": "lg2",
                    "type": "group",
                    "logic": "AND",
                    "children": [
                      {
                        "id": "c2",
                        "type": "condition",
                        "fieldName": "country",
                        "operator": "IN",
                        "threshold": "CN,RU"
                      },
                      {
                        "id": "c3",
                        "type": "condition",
                        "fieldName": "deviceCount",
                        "operator": "GE",
                        "threshold": 3
                      }
                    ]
                  }
                ]
              },
              "action": "REJECT",
              "reason": "高风险订单"
            }
          ]
        }
        """.trimIndent()

    @Test
    fun `parse 多层嵌套条件树并保留判别字段与双态阈值`() {
        val result = DslParser.parseRuleDefinition(nestedRuleJson)

        val definition = assertIs<ParseResult.Success<RuleDefinition>>(result).value
        assertEquals(RuleAction.PASS, definition.defaultAction)
        assertEquals("默认通过", definition.defaultReason)
        assertEquals(1, definition.rules.size)

        val group = definition.rules[0]
        assertEquals("rg1", group.id)
        assertEquals(RuleAction.REJECT, group.action)
        assertEquals("高风险订单", group.reason)

        // 根组：OR 逻辑组，含 1 个原子条件 + 1 个嵌套 AND 子组
        val root = assertIs<LogicGroup>(group.condition)
        assertEquals(NodeKind.GROUP, root.type)
        assertEquals(LogicType.OR, root.logic)
        assertEquals(2, root.children.size)

        // 原子条件：数字阈值保持数字形态
        val atomic = assertIs<ConditionNode>(root.children[0])
        assertEquals("c1", atomic.id)
        assertEquals(NodeKind.CONDITION, atomic.type)
        assertEquals("amount", atomic.fieldName)
        assertEquals(ConditionOperator.GT, atomic.operator)
        assertEquals(ThresholdValue.Numeric(10000.toBigDecimal()), atomic.threshold)

        // 嵌套子组：字符串阈值保持字符串形态
        val subGroup = assertIs<LogicGroup>(root.children[1])
        assertEquals(LogicType.AND, subGroup.logic)
        val inCondition = assertIs<ConditionNode>(subGroup.children[0])
        assertEquals(ThresholdValue.Text("CN,RU"), inCondition.threshold)
        assertEquals(ConditionOperator.IN, inCondition.operator)
    }

    @Test
    fun `round-trip 序列化后语义等价`() {
        val parsed = DslParser.parseRuleDefinition(nestedRuleJson).getOrThrow()

        val serialized = DslParser.write(parsed)
        val reparsed = DslParser.parseRuleDefinition(serialized).getOrThrow()

        // 数据类结构相等 = 语义等价（不要求字节级一致，键序/空白可差异）
        assertEquals(parsed, reparsed)
    }

    @Test
    fun `序列化字段名与前端逐字一致`() {
        val parsed = DslParser.parseRuleDefinition(nestedRuleJson).getOrThrow()

        val json = DslParser.write(parsed)

        // 顶层字段
        assertTrue("\"defaultAction\":" in json, "缺少 defaultAction: $json")
        assertTrue("\"defaultReason\":" in json, "缺少 defaultReason: $json")
        assertTrue("\"rules\":" in json, "缺少 rules: $json")
        // 判别字段 type 写回小写值
        assertTrue("\"type\":\"group\"" in json, "type 应写回 'group': $json")
        assertTrue("\"type\":\"condition\"" in json, "type 应写回 'condition': $json")
        // 数字阈值不带引号、字符串阈值带引号
        assertTrue("\"threshold\":10000" in json, "数字阈值应保持数字形态: $json")
        assertTrue("\"threshold\":\"CN,RU\"" in json, "字符串阈值应保持字符串形态: $json")
    }

    @Test
    fun `容忍未知字段不影响解析`() {
        // 前端迭代新增字段（如埋点、UI 元数据）不应导致后端解析失败
        val withUnknown =
            """
            {
              "defaultAction": "REJECT",
              "defaultReason": "默认拒绝",
              "futureMetadata": { "traceId": "abc", "flags": [1, 2] },
              "rules": [
                {
                  "id": "rg1",
                  "condition": {
                    "id": "c1",
                    "type": "condition",
                    "fieldName": "score",
                    "operator": "LT",
                    "threshold": 0.5,
                    "uiHint": { "x": 1 }
                  },
                  "action": "MANUAL_REVIEW",
                  "reason": "低分人工",
                  "extraProp": "ignored"
                }
              ]
            }
            """.trimIndent()

        val parsed = DslParser.parseRuleDefinition(withUnknown).getOrThrow()

        assertEquals(RuleAction.MANUAL_REVIEW, parsed.rules[0].action)
        val condition = assertIs<ConditionNode>(parsed.rules[0].condition)
        assertEquals(ThresholdValue.Numeric(0.5.toBigDecimal()), condition.threshold)
    }

    @Test
    fun `单规则配置 SingleRuleConfig round-trip`() {
        // 形状对齐前端 SingleRuleConfig：condition/action/reason/defaultAction/defaultReason
        val json =
            """
            {
              "condition": {
                "id": "c1",
                "type": "condition",
                "fieldName": "blackCardCount",
                "operator": "NOT_IN",
                "threshold": "cardA,cardB"
              },
              "action": "REJECT",
              "reason": "命中黑卡",
              "defaultAction": "PASS",
              "defaultReason": "默认通过"
            }
            """.trimIndent()

        val parsed = DslParser.parseSingleRule(json).getOrThrow()
        assertEquals(ConditionOperator.NOT_IN, (parsed.condition as ConditionNode).operator)
        assertEquals(RuleAction.PASS, parsed.defaultAction)

        val reparsed = DslParser.parseSingleRule(DslParser.write(parsed)).getOrThrow()
        assertEquals(parsed, reparsed)
    }

    @Test
    fun `空逻辑组小数阈值等边界形状可正常建模`() {
        // 小数阈值不因 Double 转换失真（0.1 保持 0.1）
        val json =
            """
            {
              "defaultAction": "PASS",
              "defaultReason": "默认通过",
              "rules": [
                {
                  "id": "rg1",
                  "condition": {
                    "id": "c1",
                    "type": "condition",
                    "fieldName": "ratio",
                    "operator": "LE",
                    "threshold": 0.1
                  },
                  "action": "PASS",
                  "reason": ""
                }
              ]
            }
            """.trimIndent()

        val parsed = DslParser.parseRuleDefinition(json).getOrThrow()
        val condition = assertIs<ConditionNode>(parsed.rules[0].condition)
        assertEquals("0.1", condition.threshold.raw.toString())
    }
}
