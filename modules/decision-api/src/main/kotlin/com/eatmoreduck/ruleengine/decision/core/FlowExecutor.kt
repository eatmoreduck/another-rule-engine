package com.eatmoreduck.ruleengine.decision.core

import com.eatmoreduck.ruleengine.decision.config.DecisionProperties
import com.eatmoreduck.ruleengine.decision.repo.NameListLookup
import com.eatmoreduck.ruleengine.domain.DecisionResult
import com.eatmoreduck.ruleengine.dsl.ActionNodeData
import com.eatmoreduck.ruleengine.dsl.BlacklistNodeData
import com.eatmoreduck.ruleengine.dsl.ConditionNodeData
import com.eatmoreduck.ruleengine.dsl.EndNodeData
import com.eatmoreduck.ruleengine.dsl.FlowEdge
import com.eatmoreduck.ruleengine.dsl.FlowGraph
import com.eatmoreduck.ruleengine.dsl.FlowNode
import com.eatmoreduck.ruleengine.dsl.MergeNodeData
import com.eatmoreduck.ruleengine.dsl.RuleSetNodeData
import com.eatmoreduck.ruleengine.dsl.StartNodeData
import com.eatmoreduck.ruleengine.dsl.ThresholdValue
import com.eatmoreduck.ruleengine.dsl.WhitelistNodeData
import com.eatmoreduck.ruleengine.engine.GroovyScriptEngine
import com.eatmoreduck.ruleengine.engine.ScriptEngineException
import com.eatmoreduck.ruleengine.engine.ScriptTimeoutException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Duration

/** 规则集引用规则的载荷来源（GrayscaleRouter 提供：主行生效版本，不走灰度） */
fun interface RuleSetPayloadSource {
    fun loadRulePayloadForRuleSet(ruleKey: String): String?
}

/**
 * 决策流图解释执行器（节点语义照搬旧 DecisionFlowExecutionService.traverseNode）。
 *
 * 节点种类：start / condition / action / end / ruleset / blacklist / whitelist / merge。
 * 分支选择：条件满足走 sourceHandle "true"/"pass"（兼容前端 data.conditionMet=true），
 * 不满足走 "false"（conditionMet=false）；无条件分支优先 "pass"/null 手柄，回退第一条出边
 * （旧 getNextNode 语义逐字保留）。
 *
 * 与旧实现的差异点：
 * - 递归遍历改为迭代 + 步数上限（[DecisionProperties.flowMaxSteps]，默认 1000），
 *   防止流图成环导致栈溢出/死循环（无后续节点的拒绝文案按节点类型区分，逐字对齐旧实现）；
 * - 动作节点收口：产生决策后沿出边走到结束节点输出（结束节点优先输出携带结果，
 *   不被默认动作覆盖），无出边的存量旧图保持直接终止；
 * - 规则集内引用规则走快照缓存（生效版本载荷），不重复解析；
 * - 全部结果携带 executionContext（旧流节点构造的响应不含该字段，统一后前端多读一个字段不破坏契约）。
 *
 * 结构：[execute] 主循环只做「取节点 → 处理 → 推进」，节点语义在各自的处理函数中
 * （一节点一函数），出口统一为 [NodeOutcome]；所有拒绝文案为对外契约，不可改字。
 *
 * 线程安全：无共享可变状态，单实例可被并发决策复用。
 */
@Component
class FlowExecutor(
    private val nameListLookup: NameListLookup,
    @Qualifier("decisionScriptEngine")
    private val scriptEngine: GroovyScriptEngine,
    private val ruleSetPayloads: RuleSetPayloadSource,
    private val properties: DecisionProperties,
) {
    private val log = LoggerFactory.getLogger(FlowExecutor::class.java)

    /**
     * 解释执行流图。
     *
     * @param graph 已解析的流图
     * @param features 特征集合
     * @param executionTimeoutMs 规则集内单条规则的执行超时
     */
    fun execute(
        graph: FlowGraph,
        features: Map<String, Any?>,
        executionTimeoutMs: Long,
    ): DecisionResult {
        val start = graph.nodes.firstOrNull { it.data is StartNodeData } ?: return rejected("决策流没有开始节点")
        var current: FlowNode = start
        var steps = 0
        // 动作节点收口后携带的锁定决策：余下节点不再执行，沿出边走完图，由结束节点输出
        var locked: DecisionResult? = null

        while (true) {
            if (++steps > properties.flowMaxSteps) {
                return rejected("决策流执行步数超限(${properties.flowMaxSteps})，疑似流图成环")
            }
            val settled = locked
            val outcome =
                if (settled != null && current.data !is EndNodeData) {
                    // 决策已锁定：中间节点直接跳过，只沿出边推进
                    val next = nextNode(current.id, graph, null)
                    if (next == null) return settled
                    advance(next, "决策已产生但路径中断")
                } else {
                    when (val data = current.data) {
                        is StartNodeData -> {
                            advance(nextNode(current.id, graph, null), "无后续节点")
                        }

                        is ConditionNodeData -> {
                            advance(
                                nextNode(current.id, graph, evaluateOperator(features[data.fieldName], data.operator.name, data.threshold)),
                                "条件分支无后续节点",
                            )
                        }

                        is ActionNodeData -> {
                            actionOutcome(data, current, graph, features)
                        }

                        is EndNodeData -> {
                            return settled ?: decisionOf(data.defaultAction.name, data.defaultReason, features)
                        }

                        is RuleSetNodeData -> {
                            ruleSetOutcome(data, current, graph, features, executionTimeoutMs)
                        }

                        is BlacklistNodeData -> {
                            blacklistOutcome(data, current, graph, features)
                        }

                        is WhitelistNodeData -> {
                            whitelistOutcome(data, current, graph, features)
                        }

                        is MergeNodeData -> {
                            advance(nextNode(current.id, graph, null), "合并节点无后续节点")
                        }
                    }
                }
            when (outcome) {
                is NodeOutcome.Reject -> {
                    return outcome.result
                }

                is NodeOutcome.Advance -> {
                    outcome.lockedResult?.let { locked = it }
                    current = outcome.next ?: return rejected(outcome.missingNextReason)
                }
            }
        }
    }

    /**
     * 动作节点：产生决策后沿出边走到结束节点输出（图上所有路径收口到 end）；
     * 无出边视为存量旧图，保持原语义直接以动作结果终止。
     */
    private fun actionOutcome(
        data: ActionNodeData,
        node: FlowNode,
        graph: FlowGraph,
        features: Map<String, Any?>,
    ): NodeOutcome {
        val result = decisionOf(data.action.name, data.reason, features)
        val next = nextNode(node.id, graph, null) ?: return NodeOutcome.Reject(result)
        return NodeOutcome.Advance(next, "动作节点未能到达结束节点", lockedResult = result)
    }

    /** 规则集节点：拒绝优先（一票否决）；通过/无引用规则继续流程 */
    private fun ruleSetOutcome(
        data: RuleSetNodeData,
        node: FlowNode,
        graph: FlowGraph,
        features: Map<String, Any?>,
        executionTimeoutMs: Long,
    ): NodeOutcome {
        if (data.ruleKeys.isEmpty()) {
            return advance(nextNode(node.id, graph, null), "规则集无引用规则")
        }
        for (ruleKey in data.ruleKeys) {
            val outcome = runRuleInRuleSet(ruleKey, features, executionTimeoutMs) ?: continue
            if (outcome.rejected) {
                log.info("规则集拒绝优先触发: node={}, ruleKey={}, reason={}", node.id, ruleKey, outcome.reason)
                return NodeOutcome.Reject(rejected(outcome.reason ?: "规则集命中拒绝规则: $ruleKey"))
            }
        }
        return advance(nextNode(node.id, graph, null), "规则集通过分支无后续节点")
    }

    /** 黑名单节点：命中拒绝（旧文案逐字），未命中/无值继续流程 */
    private fun blacklistOutcome(
        data: BlacklistNodeData,
        node: FlowNode,
        graph: FlowGraph,
        features: Map<String, Any?>,
    ): NodeOutcome {
        val value = listFeatureValue(features, data.fieldName, data.keyType)
        if (value.isNotEmpty() && isListed(data.listKey, LIST_TYPE_BLACK, data.keyType, value)) {
            return NodeOutcome.Reject(rejected("命中黑名单: ${data.keyType}=$value"))
        }
        return advance(nextNode(node.id, graph, null), "黑名单节点无后续节点")
    }

    /** 白名单节点：无值拒绝、未命中拒绝、命中继续（旧文案逐字） */
    private fun whitelistOutcome(
        data: WhitelistNodeData,
        node: FlowNode,
        graph: FlowGraph,
        features: Map<String, Any?>,
    ): NodeOutcome {
        val value = listFeatureValue(features, data.fieldName, data.keyType)
        if (value.isEmpty()) {
            return NodeOutcome.Reject(rejected("白名单校验失败: 缺少特征值 ${data.keyType}"))
        }
        if (!isListed(data.listKey, LIST_TYPE_WHITE, data.keyType, value)) {
            return NodeOutcome.Reject(rejected("未在白名单中: ${data.keyType}=$value"))
        }
        return advance(nextNode(node.id, graph, null), "白名单节点无后续节点")
    }

    /**
     * 名单节点的特征取值：[fieldName] 显式绑定优先（特征编码），
     * 回退 [keyType]（旧约定：调用方特征键 = 名单枚举名）。
     */
    private fun listFeatureValue(
        features: Map<String, Any?>,
        fieldName: String?,
        keyType: String,
    ): String = (fieldName?.let { features[it] } ?: features[keyType])?.toString().orEmpty()

    /**
     * 名单命中判定：先查节点绑定名单，非 GLOBAL 名单未命中再回退查 GLOBAL
     * （专属名单未命中不代表全局无风险，语义照搬旧实现）。
     */
    private fun isListed(
        listKey: String?,
        listType: String,
        keyType: String,
        value: String,
    ): Boolean =
        nameListLookup.existsActive(listKey.orEmpty(), listType, keyType, value) ||
            (
                !"GLOBAL".equals(listKey, ignoreCase = true) &&
                    nameListLookup.existsActive("GLOBAL", listType, keyType, value)
            )

    /**
     * 分支选择（旧 getNextNode 语义）：
     * - conditionMet == null：优先 sourceHandle == "pass" 或无手柄的出边；
     * - conditionMet == true：sourceHandle ∈ {"true","pass"} 或 data.conditionMet == true；
     * - conditionMet == false：sourceHandle == "false" 或 data.conditionMet == false；
     * - 全部不匹配 → 回退第一条出边（旧 fallback 行为）。
     */
    private fun nextNode(
        sourceId: String,
        graph: FlowGraph,
        conditionMet: Boolean?,
    ): FlowNode? {
        val outgoing = graph.edges.filter { it.source == sourceId }
        for (edge in outgoing) {
            if (conditionMet == null) {
                val handle = edge.sourceHandle
                if ("pass" == handle || handle == null) {
                    return findNode(edge.target, graph)
                }
                continue
            }
            val handleMatch =
                if (conditionMet) {
                    "true" == edge.sourceHandle || "pass" == edge.sourceHandle
                } else {
                    "false" == edge.sourceHandle
                }
            if (handleMatch || edge.data?.conditionMet == conditionMet) {
                return findNode(edge.target, graph)
            }
        }
        return outgoing.firstOrNull()?.let { findNode(it.target, graph) }
    }

    private fun findNode(
        nodeId: String,
        graph: FlowGraph,
    ): FlowNode? = graph.nodes.firstOrNull { it.id == nodeId }

    /**
     * 条件运算（旧 evaluateOperator 语义）：
     * 数值优先（双转 Double），解析失败回退字符串比较（EQ/NE/CONTAINS/NOT_CONTAINS），
     * 其余操作符/无法解析 → false。
     */
    private fun evaluateOperator(
        fieldValue: Any?,
        operator: String,
        threshold: ThresholdValue?,
    ): Boolean {
        if (fieldValue == null) return false
        val thresholdText =
            when (threshold) {
                null -> ""
                is ThresholdValue.Text -> threshold.value
                is ThresholdValue.Numeric -> threshold.value.toPlainString()
            }
        val fieldNumber = fieldValue.toString().toDoubleOrNull()
        val thresholdNumber = thresholdText.toDoubleOrNull()
        if (fieldNumber != null && thresholdNumber != null) {
            return when (operator) {
                "GT" -> fieldNumber > thresholdNumber
                "GE" -> fieldNumber >= thresholdNumber
                "LT" -> fieldNumber < thresholdNumber
                "LE" -> fieldNumber <= thresholdNumber
                "EQ" -> fieldNumber == thresholdNumber
                "NE" -> fieldNumber != thresholdNumber
                else -> false
            }
        }
        val fieldText = fieldValue.toString()
        return when (operator) {
            "EQ" -> fieldText == thresholdText
            "NE" -> fieldText != thresholdText
            "CONTAINS" -> fieldText.contains(thresholdText)
            "NOT_CONTAINS" -> !fieldText.contains(thresholdText)
            else -> false
        }
    }

    /** 执行规则集内单条规则；规则缺失/停用/载荷缺失 → 视为不拒绝 */
    private fun runRuleInRuleSet(
        ruleKey: String,
        features: Map<String, Any?>,
        executionTimeoutMs: Long,
    ): RuleExecResult? {
        val payload = ruleSetPayloads.loadRulePayloadForRuleSet(ruleKey) ?: return null
        val raw =
            try {
                // 同主链路口径：特征以 "features" 绑定变量注入
                scriptEngine.execute(payload, mapOf(DecisionService.FEATURES_VARIABLE to features), Duration.ofMillis(executionTimeoutMs))
            } catch (e: ScriptTimeoutException) {
                log.warn("规则集引用规则执行超时: ruleKey={}", ruleKey)
                return RuleExecResult(rejected = false, reason = null)
            } catch (e: ScriptEngineException) {
                log.warn("规则集引用规则执行失败: ruleKey={}", ruleKey, e)
                return RuleExecResult(rejected = false, reason = null)
            }
        return toRuleExecResult(raw ?: return RuleExecResult(rejected = false, reason = null))
    }

    /** 规则输出 → 执行结果：Map 取 decision/reason，Boolean 即拒绝位，其余按字符串比较 */
    private fun toRuleExecResult(raw: Any): RuleExecResult =
        when (raw) {
            is Map<*, *> -> RuleExecResult(rejected = "REJECT" == raw["decision"]?.toString(), reason = raw["reason"]?.toString())
            is Boolean -> RuleExecResult(rejected = !raw, reason = null)
            else -> RuleExecResult(rejected = "REJECT" == raw.toString(), reason = null)
        }

    private data class RuleExecResult(
        val rejected: Boolean,
        val reason: String?,
    )

    private fun decisionOf(
        action: String,
        reason: String,
        features: Map<String, Any?>,
    ): DecisionResult = ResultMapper.toDecision(action, features).copy(reason = reason)

    private fun rejected(reason: String): DecisionResult = DecisionResult.rejected(reason, executionTimeMs = 0)

    /** 节点处理结果：推进到下一节点（携带缺边时的拒绝文案与可选的锁定决策），或以给定结果终止 */
    private sealed interface NodeOutcome {
        data class Advance(
            val next: FlowNode?,
            val missingNextReason: String,
            val lockedResult: DecisionResult? = null,
        ) : NodeOutcome

        data class Reject(
            val result: DecisionResult,
        ) : NodeOutcome
    }

    private fun advance(
        next: FlowNode?,
        missingNextReason: String,
    ): NodeOutcome = NodeOutcome.Advance(next, missingNextReason)

    private companion object {
        private const val LIST_TYPE_BLACK = "BLACK"
        private const val LIST_TYPE_WHITE = "WHITE"
    }
}
