package com.example.ruleengine.decision.core

import com.example.ruleengine.decision.config.DecisionProperties
import com.example.ruleengine.decision.repo.NameListLookup
import com.example.ruleengine.domain.DecisionResult
import com.example.ruleengine.dsl.ActionNodeData
import com.example.ruleengine.dsl.BlacklistNodeData
import com.example.ruleengine.dsl.ConditionNodeData
import com.example.ruleengine.dsl.EndNodeData
import com.example.ruleengine.dsl.FlowEdge
import com.example.ruleengine.dsl.FlowGraph
import com.example.ruleengine.dsl.FlowNode
import com.example.ruleengine.dsl.MergeNodeData
import com.example.ruleengine.dsl.RuleSetNodeData
import com.example.ruleengine.dsl.StartNodeData
import com.example.ruleengine.dsl.ThresholdValue
import com.example.ruleengine.dsl.WhitelistNodeData
import com.example.ruleengine.engine.GroovyScriptEngine
import com.example.ruleengine.engine.ScriptEngineException
import com.example.ruleengine.engine.ScriptTimeoutException
import org.slf4j.LoggerFactory
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
 * - 规则集内引用规则走快照缓存（生效版本载荷），不重复解析；
 * - 全部结果携带 executionContext（旧流节点构造的响应不含该字段，统一后前端多读一个字段不破坏契约）。
 *
 * 线程安全：无共享可变状态，单实例可被并发决策复用。
 */
@Component
class FlowExecutor(
    private val nameListLookup: NameListLookup,
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
        var current: FlowNode? = start
        var steps = 0

        while (current != null) {
            if (++steps > properties.flowMaxSteps) {
                return rejected("决策流执行步数超限(${properties.flowMaxSteps})，疑似流图成环")
            }
            val node = current
            when (val data = node.data) {
                is StartNodeData -> {
                    current = nextNode(node.id, graph, null) ?: return rejected("无后续节点")
                }

                is ConditionNodeData -> {
                    val met = evaluateOperator(features[data.fieldName], data.operator.name, data.threshold)
                    current = nextNode(node.id, graph, met) ?: return rejected("条件分支无后续节点")
                }

                is ActionNodeData -> {
                    return decisionOf(data.action.name, data.reason, features)
                }

                is EndNodeData -> {
                    return decisionOf(data.defaultAction.name, data.defaultReason, features)
                }

                is RuleSetNodeData -> {
                    if (data.ruleKeys.isEmpty()) {
                        current = nextNode(node.id, graph, null) ?: return rejected("规则集无引用规则")
                    } else {
                        val reject = evaluateRuleSet(data.ruleKeys, node.id, features, executionTimeoutMs)
                        if (reject != null) return reject
                        current = nextNode(node.id, graph, null) ?: return rejected("规则集通过分支无后续节点")
                    }
                }

                is BlacklistNodeData -> {
                    val keyType = data.keyType
                    val value = features[keyType]?.toString().orEmpty()
                    if (value.isEmpty()) {
                        current = nextNode(node.id, graph, null) ?: return rejected("黑名单节点无后续节点")
                    } else {
                        val hit =
                            nameListLookup.existsActive(data.listKey.orEmpty(), "BLACK", keyType, value) ||
                                (
                                    !"GLOBAL".equals(data.listKey, ignoreCase = true) &&
                                        nameListLookup.existsActive("GLOBAL", "BLACK", keyType, value)
                                )
                        if (hit) return rejected("命中黑名单: $keyType=$value")
                        current = nextNode(node.id, graph, null) ?: return rejected("黑名单节点无后续节点")
                    }
                }

                is WhitelistNodeData -> {
                    val keyType = data.keyType
                    val value = features[keyType]?.toString().orEmpty()
                    if (value.isEmpty()) {
                        return rejected("白名单校验失败: 缺少特征值 $keyType")
                    }
                    val hit =
                        nameListLookup.existsActive(data.listKey.orEmpty(), "WHITE", keyType, value) ||
                            (
                                !"GLOBAL".equals(data.listKey, ignoreCase = true) &&
                                    nameListLookup.existsActive("GLOBAL", "WHITE", keyType, value)
                            )
                    if (hit) {
                        current = nextNode(node.id, graph, null) ?: return rejected("白名单节点无后续节点")
                    } else {
                        return rejected("未在白名单中: $keyType=$value")
                    }
                }

                is MergeNodeData -> {
                    current = nextNode(node.id, graph, null) ?: return rejected("合并节点无后续节点")
                }
            }
        }
        // 循环出口不可达（所有分支要么 return 要么推进 current）
        return rejected("决策流没有可执行节点")
    }

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

    /**
     * 规则集节点：拒绝优先（一票否决）。
     * 逐条执行引用规则，任一输出 REJECT 立即短路返回；全部通过 → 返回 null 继续流程。
     * 单条规则执行失败不拒绝（旧 executeRuleWithDecision 的容错语义）。
     */
    private fun evaluateRuleSet(
        ruleKeys: List<String>,
        nodeId: String,
        features: Map<String, Any?>,
        executionTimeoutMs: Long,
    ): DecisionResult? {
        for (ruleKey in ruleKeys) {
            val outcome = runRuleInRuleSet(ruleKey, features, executionTimeoutMs) ?: continue
            if (outcome.rejected) {
                log.info("规则集拒绝优先触发: node={}, ruleKey={}, reason={}", nodeId, ruleKey, outcome.reason)
                return rejected(outcome.reason ?: "规则集命中拒绝规则: $ruleKey")
            }
        }
        return null
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
        return when (raw) {
            is Map<*, *> -> {
                val decision = raw["decision"]?.toString()
                val reason = raw["reason"]?.toString()
                if (decision != null && "REJECT" == decision) {
                    RuleExecResult(rejected = true, reason = reason)
                } else {
                    RuleExecResult(rejected = false, reason = reason)
                }
            }

            is Boolean -> {
                RuleExecResult(rejected = !raw, reason = null)
            }

            else -> {
                RuleExecResult(rejected = "REJECT" == raw.toString(), reason = null)
            }
        }
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
}
