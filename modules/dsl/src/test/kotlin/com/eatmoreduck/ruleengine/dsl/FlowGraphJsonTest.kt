package com.eatmoreduck.ruleengine.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 决策流图 JSON 序列化/反序列化测试。
 *
 * 样例按前端 `frontend/src/types/flowConfig.ts` + 保存口径
 * `JSON.stringify({ nodes, edges })`（DecisionFlowEditorPage）手工构造。
 */
class FlowGraphJsonTest {
    /** 覆盖全部节点类型与条件分支边的完整样例 */
    private val fullGraphJson =
        """
        {
          "nodes": [
            {
              "id": "start-1",
              "type": "start",
              "position": { "x": 250, "y": 0 },
              "data": { "label": "开始", "nodeType": "start" }
            },
            {
              "id": "cond-1",
              "type": "condition",
              "position": { "x": 250, "y": 120 },
              "data": {
                "label": "金额校验",
                "nodeType": "condition",
                "fieldName": "amount",
                "operator": "GT",
                "threshold": 5000
              }
            },
            {
              "id": "ruleset-1",
              "type": "ruleset",
              "position": { "x": 480, "y": 120 },
              "data": { "label": "黑卡规则集", "nodeType": "ruleset", "ruleKeys": ["black_card", "high_risk_geo"] }
            },
            {
              "id": "black-1",
              "type": "blacklist",
              "position": { "x": 480, "y": 240 },
              "data": { "label": "IP 黑名单", "nodeType": "blacklist", "keyType": "IP", "listKey": "fraud_ip" }
            },
            {
              "id": "action-1",
              "type": "action",
              "position": { "x": 250, "y": 240 },
              "data": { "label": "拒绝订单", "nodeType": "action", "action": "REJECT", "reason": "金额超限" }
            },
            {
              "id": "merge-1",
              "type": "merge",
              "position": { "x": 250, "y": 300 },
              "data": { "label": "合并", "nodeType": "merge" }
            },
            {
              "id": "end-1",
              "type": "end",
              "position": { "x": 250, "y": 360 },
              "data": { "label": "结束", "nodeType": "end", "defaultAction": "PASS", "defaultReason": "默认通过" }
            }
          ],
          "edges": [
            { "id": "e1", "source": "start-1", "target": "cond-1" },
            {
              "id": "e2",
              "source": "cond-1",
              "target": "action-1",
              "sourceHandle": "true",
              "targetHandle": null,
              "data": { "label": "满足", "conditionMet": true }
            },
            {
              "id": "e3",
              "source": "cond-1",
              "target": "ruleset-1",
              "sourceHandle": "false",
              "data": { "label": "不满足", "conditionMet": false }
            },
            { "id": "e4", "source": "ruleset-1", "target": "black-1" },
            { "id": "e5", "source": "black-1", "target": "merge-1" },
            { "id": "e6", "source": "action-1", "target": "merge-1" },
            { "id": "e7", "source": "merge-1", "target": "end-1" }
          ]
        }
        """.trimIndent()

    @Test
    fun `parse 全节点类型并按 nodeType 判别`() {
        val graph = DslParser.parseFlowGraph(fullGraphJson).getOrThrow()

        assertEquals(7, graph.nodes.size)
        assertEquals(7, graph.edges.size)

        val byId = graph.nodes.associateBy { it.id }

        // 开始节点
        val start = assertIs<StartNodeData>(byId.getValue("start-1").data)
        assertEquals("开始", start.label)
        assertEquals(FlowNodeKind.START, start.nodeType)

        // 条件节点：阈值保持数字形态
        val condNode = byId.getValue("cond-1")
        assertEquals("condition", condNode.type)
        assertEquals(250.0, requireNotNull(condNode.position).x)
        val cond = assertIs<ConditionNodeData>(condNode.data)
        assertEquals("amount", cond.fieldName)
        assertEquals(ConditionOperator.GT, cond.operator)
        assertEquals(ThresholdValue.of(5000), cond.threshold)

        // 规则集节点
        val ruleset = assertIs<RuleSetNodeData>(byId.getValue("ruleset-1").data)
        assertEquals(listOf("black_card", "high_risk_geo"), ruleset.ruleKeys)

        // 黑名单节点：listKey 可选字段
        val blacklist = assertIs<BlacklistNodeData>(byId.getValue("black-1").data)
        assertEquals("IP", blacklist.keyType)
        assertEquals("fraud_ip", blacklist.listKey)

        // 决策节点
        val action = assertIs<ActionNodeData>(byId.getValue("action-1").data)
        assertEquals(RuleAction.REJECT, action.action)
        assertEquals("金额超限", action.reason)

        // 结束节点
        val end = assertIs<EndNodeData>(byId.getValue("end-1").data)
        assertEquals(RuleAction.PASS, end.defaultAction)
        assertEquals("默认通过", end.defaultReason)

        // 条件分支边：conditionMet 标记
        val trueEdge = graph.edges.first { it.id == "e2" }
        assertEquals(true, trueEdge.data?.conditionMet)
        assertEquals("true", trueEdge.sourceHandle)
        val plainEdge = graph.edges.first { it.id == "e1" }
        assertNull(plainEdge.data)
        assertNull(plainEdge.sourceHandle)
    }

    @Test
    fun `round-trip 序列化后语义等价`() {
        val parsed = DslParser.parseFlowGraph(fullGraphJson).getOrThrow()

        val reparsed = DslParser.parseFlowGraph(DslParser.write(parsed)).getOrThrow()

        assertEquals(parsed, reparsed)
    }

    @Test
    fun `序列化保留判别字段与位置`() {
        val json = DslParser.write(DslParser.parseFlowGraph(fullGraphJson).getOrThrow())

        assertTrue("\"nodeType\":\"start\"" in json, "nodeType 应写回小写值: $json")
        assertTrue("\"nodeType\":\"ruleset\"" in json, "nodeType 应写回小写值: $json")
        assertTrue("\"conditionMet\":true" in json, "conditionMet 应保留: $json")
        assertTrue("\"position\":{\"x\":250.0,\"y\":0.0}" in json, "position 应保留: $json")
        assertTrue("\"threshold\":5000" in json, "数字阈值应保持数字形态: $json")
    }

    @Test
    fun `容忍 React Flow 装饰字段`() {
        // 前端画布对象带有 selected/dragging/style 等装饰字段，后端应容忍并丢弃
        val withDecorations =
            """
            {
              "nodes": [
                {
                  "id": "start-1",
                  "type": "start",
                  "position": { "x": 0, "y": 0, "zoom": 1 },
                  "selected": false,
                  "dragging": false,
                  "style": { "width": 100 },
                  "data": { "label": "开始", "nodeType": "start", "deco": 42 }
                },
                {
                  "id": "end-1",
                  "type": "end",
                  "position": { "x": 0, "y": 200 },
                  "data": { "label": "结束", "nodeType": "end", "defaultAction": "PASS", "defaultReason": "默认通过" }
                }
              ],
              "edges": [
                { "id": "e1", "source": "start-1", "target": "end-1", "animated": true, "markerEnd": "arrowclosed" }
              ]
            }
            """.trimIndent()

        val graph = DslParser.parseFlowGraph(withDecorations).getOrThrow()

        assertEquals(2, graph.nodes.size)
        assertEquals(1, graph.edges.size)
        // position.zoom 属未知字段被丢弃，x/y 正常读取
        assertEquals(Position(0.0, 0.0), graph.nodes[0].position)
    }

    @Test
    fun `白名单与合并节点可选字段缺省解析`() {
        val json =
            """
            {
              "nodes": [
                {
                  "id": "white-1",
                  "type": "whitelist",
                  "data": { "label": "可信用户", "nodeType": "whitelist", "keyType": "DEVICE_ID" }
                },
                {
                  "id": "merge-1",
                  "type": "merge",
                  "data": { "label": "合并", "nodeType": "merge" }
                }
              ],
              "edges": []
            }
            """.trimIndent()

        val graph = DslParser.parseFlowGraph(json).getOrThrow()

        val whitelist = assertIs<WhitelistNodeData>(graph.nodes[0].data)
        assertEquals("DEVICE_ID", whitelist.keyType)
        assertNull(whitelist.listKey)
        assertEquals(FlowNodeKind.MERGE, assertIs<MergeNodeData>(graph.nodes[1].data).nodeType)
    }
}
