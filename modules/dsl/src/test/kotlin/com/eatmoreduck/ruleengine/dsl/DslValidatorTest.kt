package com.eatmoreduck.ruleengine.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 结构校验器测试：违规必须给出可读错误与具体路径。
 */
class DslValidatorTest {
    private val validator = DslValidator(maxDepth = 2)

    private fun condition(
        id: String = "c1",
        fieldName: String = "amount",
        operator: ConditionOperator = ConditionOperator.GT,
        threshold: ThresholdValue = ThresholdValue.of(100),
    ): ConditionNode = ConditionNode(id = id, fieldName = fieldName, operator = operator, threshold = threshold)

    // ============ 规则定义树 ============

    @Test
    fun `空逻辑组报错并给出路径`() {
        val definition =
            RuleDefinition(
                defaultAction = RuleAction.PASS,
                defaultReason = "默认通过",
                rules =
                    listOf(
                        RuleGroup(
                            id = "rg1",
                            condition = LogicGroup(id = "lg1", logic = LogicType.AND, children = emptyList()),
                            action = RuleAction.REJECT,
                            reason = "r",
                        ),
                    ),
            )

        val result = validator.validate(definition)

        assertFalse(result.isValid)
        val issue = result.errors.single()
        assertEquals("$.rules[0].condition", issue.path)
        assertTrue("空逻辑组" in issue.message, "应指出空逻辑组: ${issue.message}")
    }

    @Test
    fun `无条件叶子报错`() {
        // 对应前端 isEmptyCondition：fieldName 为空的原子条件
        val definition =
            RuleDefinition(
                defaultAction = RuleAction.PASS,
                defaultReason = "默认通过",
                rules =
                    listOf(
                        RuleGroup(id = "rg1", condition = condition(fieldName = "  "), action = RuleAction.REJECT, reason = "r"),
                    ),
            )

        val result = validator.validate(definition)

        assertFalse(result.isValid)
        val issue = result.errors.single()
        assertEquals("$.rules[0].condition", issue.path)
        assertTrue("fieldName" in issue.message, "应指出缺少字段名: ${issue.message}")
    }

    @Test
    fun `空白字符串阈值报错`() {
        val definition =
            RuleDefinition(
                defaultAction = RuleAction.PASS,
                defaultReason = "默认通过",
                rules =
                    listOf(
                        RuleGroup(
                            id = "rg1",
                            condition = condition(threshold = ThresholdValue.Text("  ")),
                            action = RuleAction.REJECT,
                            reason = "r",
                        ),
                    ),
            )

        val result = validator.validate(definition)

        assertFalse(result.isValid)
        assertTrue("threshold" in result.errors.single().message)
    }

    @Test
    fun `嵌套深度超限报错并指出实际深度`() {
        // maxDepth = 2：根组(第1层) → 子组(第2层) 合法，孙组(第3层) 超限
        val leaf = condition()
        val grandChild = LogicGroup(id = "lg3", logic = LogicType.OR, children = listOf(leaf))
        val child = LogicGroup(id = "lg2", logic = LogicType.AND, children = listOf(grandChild))
        val root = LogicGroup(id = "lg1", logic = LogicType.AND, children = listOf(child))
        val definition =
            RuleDefinition(
                defaultAction = RuleAction.PASS,
                defaultReason = "默认通过",
                rules = listOf(RuleGroup(id = "rg1", condition = root, action = RuleAction.REJECT, reason = "r")),
            )

        val result = validator.validate(definition)

        assertFalse(result.isValid)
        val issue = result.errors.single()
        assertEquals("$.rules[0].condition.children[0].children[0]", issue.path)
        assertTrue("超过上限" in issue.message, "应指出深度超限: ${issue.message}")
    }

    @Test
    fun `深度上限内不报错`() {
        val child = LogicGroup(id = "lg2", logic = LogicType.AND, children = listOf(condition()))
        val root = LogicGroup(id = "lg1", logic = LogicType.OR, children = listOf(child))
        val definition =
            RuleDefinition(
                defaultAction = RuleAction.PASS,
                defaultReason = "默认通过",
                rules = listOf(RuleGroup(id = "rg1", condition = root, action = RuleAction.REJECT, reason = "r")),
            )

        val result = validator.validate(definition)

        assertTrue(result.isValid, "深度内合法结构不应报错: ${result.errors}")
    }

    @Test
    fun `无任何规则给出警告但结构合法`() {
        val definition = RuleDefinition(defaultAction = RuleAction.PASS, defaultReason = "默认通过", rules = emptyList())

        val result = validator.validate(definition)

        assertTrue(result.isValid)
        assertEquals(1, result.warnings.size)
        assertTrue("$.rules" == result.warnings.single().path)
    }

    @Test
    fun `单规则配置复用节点校验`() {
        val result =
            validator.validate(
                SingleRuleConfig(
                    condition = condition(fieldName = ""),
                    action = RuleAction.REJECT,
                    reason = "",
                    defaultAction = RuleAction.PASS,
                    defaultReason = "默认通过",
                ),
            )

        assertFalse(result.isValid)
        assertEquals("$.condition", result.errors.single().path)
    }

    // ============ 决策流图 ============

    private fun node(
        id: String,
        data: FlowNodeData,
        type: String = data.nodeType.jsonValue,
    ): FlowNode = FlowNode(id = id, type = type, position = Position(0.0, 0.0), data = data)

    /** 合法基线图：start → condition →(true) action → end；(false) → end */
    private fun validGraph(): FlowGraph =
        FlowGraph(
            nodes =
                listOf(
                    node("start-1", StartNodeData("开始")),
                    node(
                        "cond-1",
                        ConditionNodeData("金额", fieldName = "amount", operator = ConditionOperator.GT, threshold = ThresholdValue.of(100)),
                    ),
                    node("action-1", ActionNodeData("拒绝", action = RuleAction.REJECT, reason = "超限")),
                    node("end-1", EndNodeData("结束", defaultAction = RuleAction.PASS, defaultReason = "默认通过")),
                ),
            edges =
                listOf(
                    FlowEdge(id = "e1", source = "start-1", target = "cond-1"),
                    FlowEdge(
                        id = "e2",
                        source = "cond-1",
                        target = "action-1",
                        sourceHandle = "true",
                        data = ConditionEdgeData(label = "满足", conditionMet = true),
                    ),
                    FlowEdge(
                        id = "e3",
                        source = "cond-1",
                        target = "end-1",
                        sourceHandle = "false",
                        data = ConditionEdgeData(label = "不满足", conditionMet = false),
                    ),
                    FlowEdge(id = "e4", source = "action-1", target = "end-1"),
                ),
        )

    @Test
    fun `合法决策流图不报错`() {
        val result = validator.validate(validGraph())

        assertTrue(result.isValid, "合法图不应有 ERROR: ${result.errors}")
        assertTrue(result.warnings.isEmpty(), "合法图不应有 WARNING: ${result.warnings}")
    }

    @Test
    fun `缺少开始节点报错`() {
        val graph =
            validGraph().let {
                it.copy(nodes = it.nodes.filterNot { n -> n.data is StartNodeData })
            }

        val result = validator.validate(graph)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { "开始节点" in it.message })
    }

    @Test
    fun `多个开始节点报错`() {
        val graph =
            validGraph().let {
                it.copy(nodes = it.nodes + node("start-2", StartNodeData("开始2")))
            }

        val result = validator.validate(graph)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { "开始节点" in it.message && "2" in it.message })
    }

    @Test
    fun `缺少结束节点告警`() {
        val graph =
            validGraph().let {
                it.copy(
                    nodes = it.nodes.filterNot { n -> n.data is EndNodeData },
                    edges = it.edges.filter { e -> e.target != "end-1" },
                )
            }

        val result = validator.validate(graph)

        assertTrue(result.isValid, "缺 end 仅为 WARNING，不阻断")
        assertTrue(result.warnings.any { "结束节点" in it.message })
    }

    @Test
    fun `外层 type 与 nodeType 不一致报错`() {
        val graph =
            validGraph().let {
                it.copy(nodes = it.nodes.map { n -> if (n.id == "cond-1") n.copy(type = "action") else n })
            }

        val result = validator.validate(graph)

        assertFalse(result.isValid)
        val issue = result.errors.single()
        assertTrue("不一致" in issue.message, "应指出 type 不一致: ${issue.message}")
        assertTrue("cond-1" in issue.path || "nodes" in issue.path)
    }

    @Test
    fun `悬空边报错`() {
        val graph =
            validGraph().let {
                it.copy(edges = it.edges + FlowEdge(id = "e-bad", source = "start-1", target = "ghost"))
            }

        val result = validator.validate(graph)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { "ghost" in it.message && "$.edges[4]" == it.path })
    }

    @Test
    fun `条件节点分支未覆盖给出警告`() {
        val graph =
            validGraph().let {
                it.copy(edges = it.edges.filterNot { e -> e.id == "e3" })
            }

        val result = validator.validate(graph)

        assertTrue(result.isValid, "缺 false 分支运行时可兜底，应为 WARNING")
        assertTrue(result.warnings.any { "conditionMet" in it.message && "cond-1" in it.path })
    }

    @Test
    fun `空规则集给出警告`() {
        val graph =
            validGraph().let {
                it.copy(nodes = it.nodes + node("rs-1", RuleSetNodeData("空规则集", ruleKeys = emptyList())))
            }
        // 从决策节点连边保证可达性，且不引入其他告警
        val connected = graph.copy(edges = graph.edges + FlowEdge(id = "e5", source = "action-1", target = "rs-1"))

        val result = validator.validate(connected)

        assertTrue(result.warnings.any { "规则集" in it.message && "rs-1" in it.path })
    }

    @Test
    fun `名单节点缺 keyType 报错`() {
        val graph =
            validGraph().let {
                it.copy(nodes = it.nodes + node("black-1", BlacklistNodeData("黑名单", keyType = "")))
            }
        val connected = graph.copy(edges = graph.edges + FlowEdge(id = "e5", source = "action-1", target = "black-1"))

        val result = validator.validate(connected)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { "keyType" in it.message })
    }

    @Test
    fun `不可达节点给出警告`() {
        val graph =
            validGraph().let {
                it.copy(nodes = it.nodes + node("orphan-1", ActionNodeData("孤岛", action = RuleAction.PASS, reason = "")))
            }

        val result = validator.validate(graph)

        assertTrue(result.isValid)
        assertTrue(result.warnings.any { "不可达" in it.message && "orphan-1" in it.path })
    }

    @Test
    fun `两节点环报错并给出环路径`() {
        val graph =
            validGraph().let {
                it.copy(edges = it.edges + FlowEdge(id = "e-back", source = "action-1", target = "cond-1"))
            }

        val result = validator.validate(graph)

        assertFalse(result.isValid)
        val cycle = result.errors.single { "环" in it.message }
        assertTrue("cond-1" in cycle.message && "action-1" in cycle.message, "应给出环中节点: ${cycle.message}")
    }

    @Test
    fun `自环报错`() {
        val graph =
            validGraph().let {
                it.copy(edges = it.edges + FlowEdge(id = "e-self", source = "cond-1", target = "cond-1"))
            }

        val result = validator.validate(graph)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { "DAG" in it.message && "cond-1" in it.message })
    }

    @Test
    fun `菱形汇合不是环`() {
        val graph =
            FlowGraph(
                nodes =
                    listOf(
                        node("start-1", StartNodeData("开始")),
                        node(
                            "cond-1",
                            ConditionNodeData(
                                "金额",
                                fieldName = "amount",
                                operator = ConditionOperator.GT,
                                threshold = ThresholdValue.of(100),
                            ),
                        ),
                        node("merge-1", MergeNodeData("合并")),
                        node("end-1", EndNodeData("结束", defaultAction = RuleAction.PASS, defaultReason = "默认通过")),
                    ),
                edges =
                    listOf(
                        FlowEdge(id = "e1", source = "start-1", target = "cond-1"),
                        FlowEdge(
                            id = "e2",
                            source = "cond-1",
                            target = "merge-1",
                            sourceHandle = "true",
                            data = ConditionEdgeData(label = "满足", conditionMet = true),
                        ),
                        FlowEdge(
                            id = "e3",
                            source = "cond-1",
                            target = "merge-1",
                            sourceHandle = "false",
                            data = ConditionEdgeData(label = "不满足", conditionMet = false),
                        ),
                        FlowEdge(id = "e4", source = "merge-1", target = "end-1"),
                    ),
            )

        val result = validator.validate(graph)

        assertTrue(result.errors.isEmpty(), "多路径汇合不应判为环: ${result.errors}")
    }

    @Test
    fun `非结束节点缺出边给出断头路警告`() {
        val graph =
            validGraph().let {
                it.copy(edges = it.edges.filterNot { e -> e.id == "e4" })
            }

        val result = validator.validate(graph)

        assertTrue(result.isValid, "断头路是 WARNING 不阻断保存: ${result.errors}")
        assertTrue(result.warnings.any { "出边" in it.message && "action-1" in it.path })
    }
}
