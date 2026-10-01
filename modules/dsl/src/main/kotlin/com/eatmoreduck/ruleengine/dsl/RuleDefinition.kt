package com.eatmoreduck.ruleengine.dsl

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * 规则动作，对齐前端 `frontend/src/types/ruleConfig.ts` 的 `Action`：
 * `'PASS' | 'REJECT' | 'MANUAL_REVIEW'`。
 *
 * 枚举名与 JSON 值逐字一致，无需命名映射。
 */
enum class RuleAction {
    PASS,
    REJECT,
    MANUAL_REVIEW,
}

/**
 * 比较运算符，对齐前端 `Operator`：
 * `'EQ' | 'NE' | 'GT' | 'GE' | 'LT' | 'LE' | 'CONTAINS' | 'NOT_CONTAINS' | 'IN' | 'NOT_IN'`。
 */
enum class ConditionOperator {
    EQ,
    NE,
    GT,
    GE,
    LT,
    LE,
    CONTAINS,
    NOT_CONTAINS,
    IN,
    NOT_IN,
}

/**
 * 逻辑组连接词，对齐前端 `logic: 'AND' | 'OR'`。
 */
enum class LogicType {
    AND,
    OR,
}

/**
 * 条件树节点种类，对齐前端判别字段 `type: 'condition' | 'group'`。
 *
 * JSON 值为小写，经 [JsonProperty] 显式映射。
 */
enum class NodeKind {
    @JsonProperty("condition")
    CONDITION,

    @JsonProperty("group")
    GROUP,
}

/**
 * 条件树节点：原子条件（叶子）或逻辑组（递归分支）。
 *
 * 由 JSON 中的 `type` 字段判别具体子类型；`type` 同时是普通属性
 * （EXISTING_PROPERTY + visible），保证序列化时原样写回。
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ConditionNode::class, name = "condition"),
    JsonSubTypes.Type(value = LogicGroup::class, name = "group"),
)
sealed interface ConditionTreeNode {
    /** 节点 ID，由前端 `genNodeId()` 生成（10 位随机串） */
    val id: String

    /** 节点种类判别值，与 JSON `type` 字段逐字对应 */
    val type: NodeKind
}

/**
 * 原子条件（叶子节点），对齐前端 `ConditionNode`：
 * `{ id, type: 'condition', fieldName, operator, threshold }`。
 */
data class ConditionNode(
    override val id: String,
    override val type: NodeKind = NodeKind.CONDITION,
    val fieldName: String,
    val operator: ConditionOperator,
    val threshold: ThresholdValue,
) : ConditionTreeNode

/**
 * 逻辑组（递归分支节点），对齐前端 `LogicGroup`：
 * `{ id, type: 'group', logic, children }`。
 */
data class LogicGroup(
    override val id: String,
    override val type: NodeKind = NodeKind.GROUP,
    val logic: LogicType,
    val children: List<ConditionTreeNode>,
) : ConditionTreeNode

/**
 * 一条规则：条件树 → 动作，对齐前端 `RuleGroup`：
 * `{ id, condition, action, reason }`。
 */
data class RuleGroup(
    val id: String,
    val condition: ConditionTreeNode,
    val action: RuleAction,
    val reason: String,
)

/**
 * 规则定义（表单模式 V2 顶层结构），对齐前端 `FormRuleConfigV2`：
 * `{ defaultAction, defaultReason, rules }`。
 *
 * 注意：前端已弃用的 V1 结构（`FormRuleConfig` / `ConditionActionRule`）
 * 从未作为 JSON 持久化到后端（旧后端只存 groovyScript），故不建模。
 */
data class RuleDefinition(
    val defaultAction: RuleAction,
    val defaultReason: String,
    val rules: List<RuleGroup>,
)

/**
 * 单条规则配置，对齐前端 `SingleRuleConfig`：
 * `{ condition, action, reason, defaultAction, defaultReason }`。
 */
data class SingleRuleConfig(
    val condition: ConditionTreeNode,
    val action: RuleAction,
    val reason: String,
    val defaultAction: RuleAction,
    val defaultReason: String,
)
