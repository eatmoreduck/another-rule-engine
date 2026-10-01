package com.eatmoreduck.ruleengine.decision.core

/**
 * 规则快照：一次决策内钉住的规则版本（灰度一致性硬性不变式——
 * 同一次决策的解析、执行、日志记录使用同一份 (ruleKey, version, definitionJson)）。
 *
 * @param pinnedVersion 钉住的版本号（灰度命中时为灰度版本，否则为生效版本）
 * @param definitionJson 规则定义载荷（rule_versions.groovy_script 原样文本）
 * @param fromCanary 是否使用灰度版本
 * @param grayscaleConfigId 运行中灰度配置 id（metrics 递增定位；无灰度时为 null）
 */
data class RuleSnapshot(
    val ruleKey: String,
    val pinnedVersion: Int,
    val definitionJson: String,
    val fromCanary: Boolean,
    val grayscaleConfigId: Long?,
)

/** 规则快照解析结果（区分旧实现的三种拒绝原因文案） */
sealed interface RuleSnapshotOutcome {
    /** 规则不存在（旧文案"规则不存在或未启用"） */
    data object NotFound : RuleSnapshotOutcome

    /** 规则已停用或已删除（旧文案"规则未启用或已删除"） */
    data object Disabled : RuleSnapshotOutcome

    /** 解析成功 */
    data class Resolved(
        val snapshot: RuleSnapshot,
    ) : RuleSnapshotOutcome
}

/**
 * 决策流快照：一次决策内钉住的流程版本。
 *
 * @param pinnedVersion 钉住的版本号（主表 activeVersion，缺省回退最新内容版本）
 * @param graphJson 流图 JSON（灰度命中时来自版本历史表）
 * @param fromCanary 是否使用灰度版本（灰度图加载失败回退主表时为 false，与旧实现一致）
 * @param grayscaleConfigId 运行中灰度配置 id
 */
data class FlowSnapshot(
    val flowKey: String,
    val pinnedVersion: Int,
    val graphJson: String,
    val fromCanary: Boolean,
    val grayscaleConfigId: Long?,
)

/** 决策流快照解析结果 */
sealed interface FlowSnapshotOutcome {
    /** 流不存在或未启用（旧文案"决策流不存在或未启用"） */
    data object NotFound : FlowSnapshotOutcome

    /** 解析成功 */
    data class Resolved(
        val snapshot: FlowSnapshot,
    ) : FlowSnapshotOutcome
}
