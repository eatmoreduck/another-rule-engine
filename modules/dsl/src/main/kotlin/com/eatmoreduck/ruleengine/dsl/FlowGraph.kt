package com.eatmoreduck.ruleengine.dsl

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * 流程图节点种类，对齐前端 `frontend/src/types/flowConfig.ts` 的
 * `nodeType: 'start' | 'end' | 'condition' | 'action' | 'ruleset' | 'blacklist' | 'whitelist' | 'merge'`。
 *
 * [jsonValue] 为 JSON 中的小写值（与 [JsonProperty] 映射一致），供校验器比对用。
 */
enum class FlowNodeKind(
    val jsonValue: String,
) {
    @JsonProperty("start")
    START("start"),

    @JsonProperty("end")
    END("end"),

    @JsonProperty("condition")
    CONDITION("condition"),

    @JsonProperty("action")
    ACTION("action"),

    @JsonProperty("ruleset")
    RULESET("ruleset"),

    @JsonProperty("blacklist")
    BLACKLIST("blacklist"),

    @JsonProperty("whitelist")
    WHITELIST("whitelist"),

    @JsonProperty("merge")
    MERGE("merge"),
}

/**
 * 流程节点数据：由 JSON 中的 `data.nodeType` 字段判别具体子类型。
 *
 * 对齐前端 `flowConfig.ts` 的 `XxxNodeData` 系列接口。
 * 前后端各有读取口径：前端生成器读 `data.nodeType`，
 * 旧后端（DecisionFlowExecutionService）读外层 `FlowNode.type`，两者应一致
 * （一致性由 [DslValidator] 检查）。
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "nodeType",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = StartNodeData::class, name = "start"),
    JsonSubTypes.Type(value = EndNodeData::class, name = "end"),
    JsonSubTypes.Type(value = ConditionNodeData::class, name = "condition"),
    JsonSubTypes.Type(value = ActionNodeData::class, name = "action"),
    JsonSubTypes.Type(value = RuleSetNodeData::class, name = "ruleset"),
    JsonSubTypes.Type(value = BlacklistNodeData::class, name = "blacklist"),
    JsonSubTypes.Type(value = WhitelistNodeData::class, name = "whitelist"),
    JsonSubTypes.Type(value = MergeNodeData::class, name = "merge"),
)
sealed interface FlowNodeData {
    /** 画布展示名（前端标签，如「开始」「金额校验」） */
    val label: String

    /** 节点种类判别值，与 JSON `data.nodeType` 字段逐字对应 */
    val nodeType: FlowNodeKind
}

/** 开始节点数据：`{ label, nodeType: 'start' }` */
data class StartNodeData(
    override val label: String,
    override val nodeType: FlowNodeKind = FlowNodeKind.START,
) : FlowNodeData

/** 结束节点数据：`{ label, nodeType: 'end', defaultAction, defaultReason }` */
data class EndNodeData(
    override val label: String,
    override val nodeType: FlowNodeKind = FlowNodeKind.END,
    val defaultAction: RuleAction,
    val defaultReason: String,
) : FlowNodeData

/** 分支内单个子条件 */
data class ConditionItem(
    val fieldName: String,
    val operator: ConditionOperator,
    val threshold: ThresholdValue,
)

/**
 * 条件节点的单个分支：组内子条件按 [match]（ALL=全部满足 / ANY=任一满足）聚合，
 * 命中走 sourceHandle=id 的出边。兜底出口的 sourceHandle 固定为 `false`（旧单条件图）
 * 或 `else`（新多分支图）。
 *
 * 旧契约兼容：conditions 为空时由 fieldName/operator/threshold 归一化为单条件（[effectiveConditions]）。
 */
data class ConditionBranch(
    val id: String,
    val match: String? = null,
    val conditions: List<ConditionItem> = emptyList(),
    // 旧单条件契约字段，仅旧图反序列化承载；新图不再写入
    val fieldName: String? = null,
    val operator: ConditionOperator? = null,
    val threshold: ThresholdValue? = null,
) {
    /** 聚合方式：ALL（全部满足，默认）/ ANY（任一满足） */
    val effectiveMatch: String
        get() = match?.uppercase() ?: "ALL"

    /** 归一化子条件列表：显式 conditions 优先，旧单条件迁移为单元素列表 */
    val effectiveConditions: List<ConditionItem>
        get() =
            conditions.ifEmpty {
                listOfNotNull(
                    fieldName?.takeIf { it.isNotBlank() }?.let {
                        ConditionItem(
                            fieldName = it,
                            operator = operator ?: ConditionOperator.GT,
                            threshold = threshold ?: ThresholdValue.of(0),
                        )
                    },
                )
            }
}

/**
 * 条件节点数据。
 *
 * 新图：`{ label, nodeType: 'condition', branches: [{ id, match, conditions: [...] }, ...] }`
 * —— 顺序匹配，第一个命中（组内按 match 聚合）走对应分支；全不命中走兜底（sourceHandle=`else`）。
 *
 * 旧图（单条件）：`{ fieldName, operator, threshold }` 经 [effectiveBranches] 归一化为
 * 单分支（id=`true`），兜底 handle 为 `false`，旧边与新执行语义完全兼容。
 */
data class ConditionNodeData(
    override val label: String,
    override val nodeType: FlowNodeKind = FlowNodeKind.CONDITION,
    val branches: List<ConditionBranch> = emptyList(),
    // 旧单条件契约字段，仅旧图反序列化承载；新图不再写入
    val fieldName: String? = null,
    val operator: ConditionOperator? = null,
    val threshold: ThresholdValue? = null,
) : FlowNodeData {
    /** 归一化分支列表：显式 branches 优先，旧单条件迁移为 id=`true` 的单分支 */
    val effectiveBranches: List<ConditionBranch>
        get() =
            branches.ifEmpty {
                listOfNotNull(
                    fieldName?.takeIf { it.isNotBlank() }?.let {
                        ConditionBranch(
                            id = "true",
                            fieldName = it,
                            operator = operator ?: ConditionOperator.GT,
                            threshold = threshold ?: ThresholdValue.of(0),
                        )
                    },
                )
            }

    /** 兜底出口的 sourceHandle：旧单条件图为 `false`，多分支图为 `else` */
    val elseHandle: String
        get() = if (branches.isEmpty()) "false" else "else"
}

/** 决策节点数据：`{ label, nodeType: 'action', action, reason }` */
data class ActionNodeData(
    override val label: String,
    override val nodeType: FlowNodeKind = FlowNodeKind.ACTION,
    val action: RuleAction,
    val reason: String,
) : FlowNodeData

/** 规则集节点数据：`{ label, nodeType: 'ruleset', ruleKeys }`，ruleKeys 为引用规则的 Key 列表 */
data class RuleSetNodeData(
    override val label: String,
    override val nodeType: FlowNodeKind = FlowNodeKind.RULESET,
    val ruleKeys: List<String> = emptyList(),
) : FlowNodeData

/**
 * 黑名单节点数据：`{ label, nodeType: 'blacklist', keyType, listKey?, fieldName? }`。
 *
 * [fieldName] 特征绑定（2026-10 新增）：从特征取值的键。缺省时回退 [keyType]（旧约定：
 * 调用方特征键直接使用名单键类型枚举名）。名单匹配仍按 [keyType] 查询。
 */
data class BlacklistNodeData(
    override val label: String,
    override val nodeType: FlowNodeKind = FlowNodeKind.BLACKLIST,
    val keyType: String,
    val listKey: String? = null,
    val fieldName: String? = null,
) : FlowNodeData

/**
 * 白名单节点数据：`{ label, nodeType: 'whitelist', keyType, listKey?, fieldName? }`。
 * [fieldName] 语义同 [BlacklistNodeData]。
 */
data class WhitelistNodeData(
    override val label: String,
    override val nodeType: FlowNodeKind = FlowNodeKind.WHITELIST,
    val keyType: String,
    val listKey: String? = null,
    val fieldName: String? = null,
) : FlowNodeData

/** 合并节点数据：`{ label, nodeType: 'merge' }` */
data class MergeNodeData(
    override val label: String,
    override val nodeType: FlowNodeKind = FlowNodeKind.MERGE,
) : FlowNodeData

/**
 * React Flow 画布坐标。旧后端未读取该字段（仅前端编辑器布局用），建模为可选以完整 round-trip。
 */
data class Position(
    val x: Double,
    val y: Double,
)

/**
 * 流程图节点（React Flow 节点导出形状）：`{ id, type, position, data }`。
 *
 * - `type` 为 React Flow 节点类型字符串，与 `data.nodeType` 同值；
 *   建模为 String 以容忍历史数据，一致性交给 [DslValidator]。
 * - `position` 为画布坐标，可选（历史数据可能缺失）。
 * - React Flow 的其他装饰字段（selected/dragging/style 等）由 Jackson 默认行为容忍并丢弃。
 */
data class FlowNode(
    val id: String,
    val type: String? = null,
    val position: Position? = null,
    val data: FlowNodeData,
)

/**
 * 条件分支边数据，对齐前端 `ConditionEdgeData`：`{ label?, conditionMet? }`。
 * `conditionMet` 仅出现在条件节点的分支出边上：true = 满足分支，false = 不满足分支。
 */
data class ConditionEdgeData(
    val label: String? = null,
    val conditionMet: Boolean? = null,
)

/**
 * 流程图边（React Flow 边导出形状）：`{ id, source, target, sourceHandle?, targetHandle?, data? }`。
 *
 * 旧后端按 `sourceHandle`（"true"/"pass"/null）选择条件分支；前端按
 * `data.conditionMet` 标记分支。两者并存，均可为空（普通顺序边）。
 */
data class FlowEdge(
    val id: String,
    val source: String,
    val target: String,
    val sourceHandle: String? = null,
    val targetHandle: String? = null,
    val data: ConditionEdgeData? = null,
)

/**
 * 决策流图顶层结构，对齐前端保存口径 `JSON.stringify({ nodes, edges })`。
 */
data class FlowGraph(
    val nodes: List<FlowNode> = emptyList(),
    val edges: List<FlowEdge> = emptyList(),
)
