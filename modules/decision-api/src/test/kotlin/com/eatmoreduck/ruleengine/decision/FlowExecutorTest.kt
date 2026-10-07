package com.eatmoreduck.ruleengine.decision

import com.eatmoreduck.ruleengine.decision.config.DecisionProperties
import com.eatmoreduck.ruleengine.decision.core.FlowExecutor
import com.eatmoreduck.ruleengine.decision.repo.NameListLookup
import com.eatmoreduck.ruleengine.dsl.DslParser
import com.eatmoreduck.ruleengine.engine.GroovyScriptEngine
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 决策流图解释执行单测：节点语义与旧 DecisionFlowExecutionService 对齐
 * （condition 双口径分支、blacklist/whitelist、ruleset 拒绝优先、防环）。
 */
class FlowExecutorTest {
    private class StubNameList(
        private val entries: Set<String> = emptySet(),
    ) : NameListLookup {
        override fun existsActive(
            listKey: String,
            listType: String,
            keyType: String,
            keyValue: String,
        ): Boolean = "$listKey|$listType|$keyType|$keyValue" in entries
    }

    private fun engine(): GroovyScriptEngine = GroovyScriptEngine(defaultExecutionTimeout = Duration.ofMillis(200))

    private fun executor(
        nameList: NameListLookup = StubNameList(),
        rulePayloads: Map<String, String> = emptyMap(),
    ): FlowExecutor = FlowExecutor(nameList, engine(), { ruleKey -> rulePayloads[ruleKey] }, DecisionProperties())

    private fun run(
        graphJson: String,
        features: Map<String, Any?>,
        nameList: NameListLookup = StubNameList(),
        rulePayloads: Map<String, String> = emptyMap(),
    ): com.eatmoreduck.ruleengine.domain.DecisionResult =
        executor(nameList, rulePayloads).execute(
            requireNotNull(DslParser.parseFlowGraph(graphJson).getOrNull()) { "流图解析失败" },
            features,
            executionTimeoutMs = 200,
        )

    // ---------- 条件节点 ----------

    @Test
    fun `condition routes true branch on numeric comparison`() {
        val graph =
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"c","type":"condition","data":{"label":"金额","nodeType":"condition","fieldName":"order_amount","operator":"GT","threshold":1000}},
              {"id":"r","type":"action","data":{"label":"拒绝","nodeType":"action","action":"REJECT","reason":"金额超限"}},
              {"id":"p","type":"action","data":{"label":"放行","nodeType":"action","action":"PASS","reason":"正常"}}
            ],"edges":[
              {"id":"e1","source":"s","target":"c"},
              {"id":"e2","source":"c","target":"r","sourceHandle":"true"},
              {"id":"e3","source":"c","target":"p","sourceHandle":"false"}
            ]}
            """.trimIndent()
        assertEquals("REJECT", run(graph, mapOf("order_amount" to 1500)).action.name)
        assertEquals("金额超限", run(graph, mapOf("order_amount" to 1500)).reason)
        assertEquals("PASS", run(graph, mapOf("order_amount" to 100)).action.name)
    }

    @Test
    fun `condition falls back to string comparison for non numeric values`() {
        val graph =
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"c","type":"condition","data":{"label":"地区","nodeType":"condition","fieldName":"region","operator":"EQ","threshold":"US"}},
              {"id":"r","type":"action","data":{"label":"拒绝","nodeType":"action","action":"REJECT","reason":"受限地区"}},
              {"id":"p","type":"action","data":{"label":"放行","nodeType":"action","action":"PASS","reason":"正常"}}
            ],"edges":[
              {"id":"e1","source":"s","target":"c"},
              {"id":"e2","source":"c","target":"r","sourceHandle":"true"},
              {"id":"e3","source":"c","target":"p","sourceHandle":"false"}
            ]}
            """.trimIndent()
        assertEquals("REJECT", run(graph, mapOf("region" to "US")).action.name)
        assertEquals("PASS", run(graph, mapOf("region" to "CN")).action.name)
    }

    @Test
    fun `front end conditionMet edge data participates in branch selection`() {
        // 前端口径：sourceHandle 缺省时按 data.conditionMet 选分支
        val graph =
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"c","type":"condition","data":{"label":"风险","nodeType":"condition","fieldName":"risk","operator":"GT","threshold":5}},
              {"id":"r","type":"action","data":{"label":"拒绝","nodeType":"action","action":"REJECT","reason":"高风险"}},
              {"id":"p","type":"action","data":{"label":"放行","nodeType":"action","action":"PASS","reason":"正常"}}
            ],"edges":[
              {"id":"e1","source":"s","target":"c"},
              {"id":"e2","source":"c","target":"r","data":{"conditionMet":true}},
              {"id":"e3","source":"c","target":"p","data":{"conditionMet":false}}
            ]}
            """.trimIndent()
        assertEquals("REJECT", run(graph, mapOf("risk" to 9)).action.name)
        assertEquals("PASS", run(graph, mapOf("risk" to 1)).action.name)
    }

    @Test
    fun `blacklist resolves value from fieldName feature binding`() {
        // 2026-10 特征绑定：fieldName 指向特征编码（device_id），名单匹配仍按 keyType（DEVICE_ID）查询；
        // 修复前取值只看 keyType（features["DEVICE_ID"]），特征编码输入永远取不到值导致名单失效
        val graph =
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"b","type":"blacklist","data":{"label":"黑名单","nodeType":"blacklist","keyType":"DEVICE_ID","listKey":"GLOBAL","fieldName":"device_id"}},
              {"id":"p","type":"action","data":{"label":"放行","nodeType":"action","action":"REJECT","reason":"正常"}}
            ],"edges":[
              {"id":"e1","source":"s","target":"b"},
              {"id":"e2","source":"b","target":"p"}
            ]}
            """.trimIndent()
        val entries = setOf("GLOBAL|BLACK|DEVICE_ID|D-999")
        assertEquals("REJECT", run(graph, mapOf("device_id" to "D-999"), nameList = StubNameList(entries)).action.name)
        assertEquals("命中黑名单: DEVICE_ID=D-999", run(graph, mapOf("device_id" to "D-999"), nameList = StubNameList(entries)).reason)
        // 未命中：fieldName 取值成功但不在名单 → 继续走到后续 action 节点
        val miss = run(graph, mapOf("device_id" to "D-100"), nameList = StubNameList(entries))
        assertEquals("REJECT", miss.action.name)
        assertEquals("正常", miss.reason)
    }

    @Test
    fun `whitelist resolves value from fieldName feature binding`() {
        val graph =
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"w","type":"whitelist","data":{"label":"白名单","nodeType":"whitelist","keyType":"USER_LEVEL","listKey":"GLOBAL","fieldName":"user_level"}},
              {"id":"p","type":"action","data":{"label":"放行","nodeType":"action","action":"REJECT","reason":"正常"}}
            ],"edges":[
              {"id":"e1","source":"s","target":"w"},
              {"id":"e2","source":"w","target":"p"}
            ]}
            """.trimIndent()
        val entries = setOf("GLOBAL|WHITE|USER_LEVEL|VIP")
        // 命中：继续走到后续 action 节点；未命中：白名单语义直接拒绝
        val hit = run(graph, mapOf("user_level" to "VIP"), nameList = StubNameList(entries))
        assertEquals("REJECT", hit.action.name)
        assertEquals("正常", hit.reason)
        val miss = run(graph, mapOf("user_level" to "NORMAL"), nameList = StubNameList(entries))
        assertEquals("REJECT", miss.action.name)
        assertEquals("未在白名单中: USER_LEVEL=NORMAL", miss.reason)
    }

    @Test
    fun `blacklist falls back to keyType when fieldName absent`() {
        // 向后兼容：无 fieldName 时维持旧约定（调用方特征键 = 名单枚举名）
        val graph =
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"b","type":"blacklist","data":{"label":"黑名单","nodeType":"blacklist","keyType":"PHONE_NO","listKey":"GLOBAL"}},
              {"id":"p","type":"action","data":{"label":"放行","nodeType":"action","action":"REJECT","reason":"正常"}}
            ],"edges":[
              {"id":"e1","source":"s","target":"b"},
              {"id":"e2","source":"b","target":"p"}
            ]}
            """.trimIndent()
        val entries = setOf("GLOBAL|BLACK|PHONE_NO|13800000001")
        assertEquals("REJECT", run(graph, mapOf("PHONE_NO" to "13800000001"), nameList = StubNameList(entries)).action.name)
    }

    // ---------- 黑白名单节点 ----------

    @Test
    fun `blacklist hit rejects with legacy reason`() {
        val graph =
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"b","type":"blacklist","data":{"label":"黑名单","nodeType":"blacklist","keyType":"userId","listKey":"GLOBAL"}},
              {"id":"p","type":"action","data":{"label":"放行","nodeType":"action","action":"PASS","reason":"正常"}}
            ],"edges":[
              {"id":"e1","source":"s","target":"b"},
              {"id":"e2","source":"b","target":"p"}
            ]}
            """.trimIndent()
        val entries = setOf("GLOBAL|BLACK|userId|bad-guy")
        assertEquals("REJECT", run(graph, mapOf("userId" to "bad-guy"), nameList = StubNameList(entries)).action.name)
        assertEquals("命中黑名单: userId=bad-guy", run(graph, mapOf("userId" to "bad-guy"), nameList = StubNameList(entries)).reason)
        assertEquals("PASS", run(graph, mapOf("userId" to "good-guy"), nameList = StubNameList(entries)).action.name)
    }

    @Test
    fun `whitelist miss rejects with legacy reason`() {
        val graph =
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"w","type":"whitelist","data":{"label":"白名单","nodeType":"whitelist","keyType":"userId","listKey":"GLOBAL"}},
              {"id":"p","type":"action","data":{"label":"放行","nodeType":"action","action":"PASS","reason":"正常"}}
            ],"edges":[
              {"id":"e1","source":"s","target":"w"},
              {"id":"e2","source":"w","target":"p"}
            ]}
            """.trimIndent()
        val entries = setOf("GLOBAL|WHITE|userId|vip-1")
        assertEquals("PASS", run(graph, mapOf("userId" to "vip-1"), nameList = StubNameList(entries)).action.name)
        assertEquals("REJECT", run(graph, mapOf("userId" to "stranger"), nameList = StubNameList(entries)).action.name)
        assertEquals("未在白名单中: userId=stranger", run(graph, mapOf("userId" to "stranger"), nameList = StubNameList(entries)).reason)
    }

    // ---------- 规则集节点 ----------

    @Test
    fun `ruleset rejects on first rejecting rule with reason`() {
        val graph =
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"rs","type":"ruleset","data":{"label":"规则集","nodeType":"ruleset","ruleKeys":["rule_a","rule_b"]}},
              {"id":"p","type":"action","data":{"label":"放行","nodeType":"action","action":"PASS","reason":"正常"}}
            ],"edges":[
              {"id":"e1","source":"s","target":"rs"},
              {"id":"e2","source":"rs","target":"p"}
            ]}
            """.trimIndent()
        val payloads =
            mapOf(
                // rule_a 放行、rule_b 拒绝（拒绝优先一票否决）
                "rule_a" to "return 'PASS'",
                "rule_b" to "return [decision: 'REJECT', reason: '命中欺诈名单']",
            )
        val result = run(graph, emptyMap(), rulePayloads = payloads)
        assertEquals("REJECT", result.action.name)
        assertEquals("命中欺诈名单", result.reason)
    }

    @Test
    fun `ruleset failure is tolerated and flow continues`() {
        val graph =
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"rs","type":"ruleset","data":{"label":"规则集","nodeType":"ruleset","ruleKeys":["missing_rule"]}},
              {"id":"e","type":"end","data":{"label":"结束","nodeType":"end","defaultAction":"PASS","defaultReason":"通过"}}
            ],"edges":[
              {"id":"e1","source":"s","target":"rs"},
              {"id":"e2","source":"rs","target":"e"}
            ]}
            """.trimIndent()
        // 引用规则不存在 → 视为不拒绝，流程继续到结束节点
        assertEquals("PASS", run(graph, emptyMap()).action.name)
    }

    // ---------- 防环与结构异常 ----------

    @Test
    fun `cyclic graph is cut off by step limit`() {
        // 条件节点自环 + 恒真分支 → 步数超限拒绝（旧递归实现此处栈溢出）
        val cyclicGraph =
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"c","type":"condition","data":{"label":"环","nodeType":"condition","fieldName":"x","operator":"GE","threshold":0}}
            ],"edges":[
              {"id":"e1","source":"s","target":"c"},
              {"id":"e2","source":"c","target":"c","sourceHandle":"true"}
            ]}
            """.trimIndent()
        val result = run(cyclicGraph, mapOf("x" to 1))
        assertEquals("REJECT", result.action.name)
        assertTrue(result.reason!!.contains("步数超限"))
    }

    @Test
    fun `graph without start node rejects`() {
        val graph =
            """{"nodes":[{"id":"a","type":"action","data":{"label":"动作","nodeType":"action","action":"PASS","reason":"ok"}}],"edges":[]}"""
        val result = run(graph, emptyMap())
        assertEquals("REJECT", result.action.name)
        assertEquals("决策流没有开始节点", result.reason)
    }

    @Test
    fun `merge node passes through to next node`() {
        val graph =
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"m","type":"merge","data":{"label":"合并","nodeType":"merge"}},
              {"id":"e","type":"end","data":{"label":"结束","nodeType":"end","defaultAction":"MANUAL_REVIEW","defaultReason":"转人工"}}
            ],"edges":[
              {"id":"e1","source":"s","target":"m"},
              {"id":"e2","source":"m","target":"e"}
            ]}
            """.trimIndent()
        val result = run(graph, emptyMap())
        assertEquals("MANUAL_REVIEW", result.action.name)
        assertEquals("转人工", result.reason)
    }

    // ---------- 动作节点收口 ----------

    @Test
    fun `branch condition group aggregates with ALL and ANY`() {
        val graph =
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"c","type":"condition","data":{"label":"组合条件","nodeType":"condition","branches":[
                {"id":"b1","match":"ALL","conditions":[
                  {"fieldName":"amount","operator":"GT","threshold":1000},
                  {"fieldName":"region","operator":"CONTAINS","threshold":"US"}
                ]},
                {"id":"b2","match":"ANY","conditions":[
                  {"fieldName":"region","operator":"CONTAINS","threshold":"新疆"},
                  {"fieldName":"region","operator":"CONTAINS","threshold":"西藏"}
                ]}
              ]}},
              {"id":"a1","type":"action","data":{"label":"组合命中","nodeType":"action","action":"REJECT","reason":"组合命中"}},
              {"id":"a2","type":"action","data":{"label":"任一命中","nodeType":"action","action":"REJECT","reason":"任一命中"}},
              {"id":"e","type":"end","data":{"label":"结束","nodeType":"end","defaultAction":"PASS","defaultReason":"通过"}}
            ],"edges":[
              {"id":"e0","source":"s","target":"c"},
              {"id":"eb1","source":"c","target":"a1","sourceHandle":"b1"},
              {"id":"eb2","source":"c","target":"a2","sourceHandle":"b2"},
              {"id":"ee","source":"c","target":"e","sourceHandle":"else"}
            ]}
            """.trimIndent()
        // ALL:两条件都满足才走 b1
        assertEquals("组合命中", run(graph, mapOf("amount" to 2000, "region" to "US East")).reason)
        // ALL 不满足(amount 高但 region 无 US)→ b2 ANY:region 含"新疆"命中
        assertEquals("任一命中", run(graph, mapOf("amount" to 2000, "region" to "CN 新疆")).reason)
        // ANY:命中任一即走 b2
        assertEquals("任一命中", run(graph, mapOf("amount" to 500, "region" to "西藏")).reason)
        assertEquals("通过", run(graph, mapOf("amount" to 500, "region" to "CN")).reason)
    }

    @Test
    fun `multi branch condition routes by first match with else fallback`() {
        val graph =
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"c","type":"condition","data":{"label":"多分支","nodeType":"condition","branches":[
                {"id":"b1","fieldName":"amount","operator":"GT","threshold":1000},
                {"id":"b2","fieldName":"region","operator":"CONTAINS","threshold":"US"}
              ]}},
              {"id":"a1","type":"action","data":{"label":"大额","nodeType":"action","action":"REJECT","reason":"大额"}},
              {"id":"a2","type":"action","data":{"label":"受限地区","nodeType":"action","action":"REJECT","reason":"受限地区"}},
              {"id":"e","type":"end","data":{"label":"结束","nodeType":"end","defaultAction":"PASS","defaultReason":"通过"}}
            ],"edges":[
              {"id":"e0","source":"s","target":"c"},
              {"id":"eb1","source":"c","target":"a1","sourceHandle":"b1"},
              {"id":"eb2","source":"c","target":"a2","sourceHandle":"b2"},
              {"id":"ee","source":"c","target":"e","sourceHandle":"else"}
            ]}
            """.trimIndent()
        // b1 顺序匹配优先：amount 命中即走 b1，不再看 b2
        assertEquals("大额", run(graph, mapOf("amount" to 2000, "region" to "US East")).reason)
        assertEquals("受限地区", run(graph, mapOf("amount" to 100, "region" to "US East")).reason)
        assertEquals("通过", run(graph, mapOf("amount" to 100, "region" to "CN")).reason)
    }

    @Test
    fun `action reject travels to end node and keeps action result`() {
        val graph =
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"act","type":"action","data":{"label":"拒绝","nodeType":"action","action":"REJECT","reason":"高风险地区拦截"}},
              {"id":"e","type":"end","data":{"label":"结束","nodeType":"end","defaultAction":"PASS","defaultReason":"默认通过"}}
            ],"edges":[
              {"id":"e1","source":"s","target":"act"},
              {"id":"e2","source":"act","target":"e"}
            ]}
            """.trimIndent()
        val result = run(graph, emptyMap())
        assertEquals("REJECT", result.action.name)
        assertEquals("高风险地区拦截", result.reason)
    }

    @Test
    fun `end node does not override locked action result`() {
        val graph =
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"act","type":"action","data":{"label":"放行","nodeType":"action","action":"PASS","reason":"动作放行"}},
              {"id":"e","type":"end","data":{"label":"结束","nodeType":"end","defaultAction":"REJECT","defaultReason":"结束拒绝"}}
            ],"edges":[
              {"id":"e1","source":"s","target":"act"},
              {"id":"e2","source":"act","target":"e"}
            ]}
            """.trimIndent()
        val result = run(graph, emptyMap())
        assertEquals("PASS", result.action.name)
        assertEquals("动作放行", result.reason)
    }

    @Test
    fun `action without outgoing edge terminates directly for legacy graphs`() {
        val graph =
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"act","type":"action","data":{"label":"拒绝","nodeType":"action","action":"REJECT","reason":"存量直停"}},
              {"id":"e","type":"end","data":{"label":"结束","nodeType":"end","defaultAction":"PASS","defaultReason":"默认通过"}}
            ],"edges":[
              {"id":"e1","source":"s","target":"act"}
            ]}
            """.trimIndent()
        val result = run(graph, emptyMap())
        assertEquals("REJECT", result.action.name)
        assertEquals("存量直停", result.reason)
    }

    @Test
    fun `locked result skips intermediate nodes until end`() {
        val graph =
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"act","type":"action","data":{"label":"拒绝","nodeType":"action","action":"REJECT","reason":"动作拒绝"}},
              {"id":"m","type":"merge","data":{"label":"合并","nodeType":"merge"}},
              {"id":"e","type":"end","data":{"label":"结束","nodeType":"end","defaultAction":"PASS","defaultReason":"默认通过"}}
            ],"edges":[
              {"id":"e1","source":"s","target":"act"},
              {"id":"e2","source":"act","target":"m"},
              {"id":"e3","source":"m","target":"e"}
            ]}
            """.trimIndent()
        val result = run(graph, emptyMap())
        assertEquals("REJECT", result.action.name)
        assertEquals("动作拒绝", result.reason)
    }
}
