package com.eatmoreduck.ruleengine.domain

import java.time.Instant

/**
 * 规则版本。
 *
 * 对应旧实体 RuleVersion（rule_versions 表），提取业务概念、剔除持久化痕迹：
 * - 剔除 JPA 注解、数据库代理主键 id、内部代理关联 ruleId（聚合间以 ruleKey 逻辑关联）
 * - 规则定义载荷固定为 [definitionJson] 原样字符串：本模块零内部依赖，
 *   不引用任何 DSL 类型，载荷的解析与校验属于执行/校验模块的职责
 *
 * 版本号语义（沿用旧模型）：
 * - version 从 1 起单调递增，同一 ruleKey 下唯一
 * - 回滚不是"指向历史版本"，而是以更高版本号落地一份历史内容的副本
 *   （旧 rollbackToVersion：newVersion = currentVersion + 1），故 [rollbackFromVersion]
 *   记录回滚所依据的历史版本号
 *
 * 不变式：
 * - ruleKey / changedBy 非空白
 * - version >= 1
 * - definitionJson 非空白（空脚本是配置错误，fail fast）
 * - isRollback=true 时 rollbackFromVersion 必填且不得等于自身版本号
 * - isRollback=false 时 rollbackFromVersion 必须为空
 *
 * 状态迁移经 [VersionStatus.transition] 严格校验，非法迁移抛 [IllegalTransitionException]。
 */
data class RuleVersion(
    val ruleKey: String,
    val version: Int,
    /** 规则定义载荷：原样保存的 JSON 字符串，本层不解释其内容 */
    val definitionJson: String,
    val status: VersionStatus = VersionStatus.DRAFT,
    val changeReason: String? = null,
    val changedBy: String,
    val changedAt: Instant,
    val isRollback: Boolean = false,
    val rollbackFromVersion: Int? = null,
) {
    init {
        require(ruleKey.isNotBlank()) { "ruleKey 不能为空白" }
        require(changedBy.isNotBlank()) { "changedBy 不能为空白" }
        require(version >= 1) { "version 必须 >= 1，实际: $version" }
        require(definitionJson.isNotBlank()) { "definitionJson（规则定义载荷）不能为空白" }
        if (isRollback) {
            require(rollbackFromVersion != null) { "回滚版本必须记录 rollbackFromVersion" }
            require(rollbackFromVersion >= 1) { "rollbackFromVersion 必须 >= 1，实际: $rollbackFromVersion" }
            require(rollbackFromVersion != version) { "rollbackFromVersion 不得等于自身版本号" }
        } else {
            require(rollbackFromVersion == null) { "非回滚版本不允许携带 rollbackFromVersion" }
        }
    }

    /** 进入灰度验证（DRAFT -> CANARY） */
    fun startCanary(): RuleVersion = transitionTo(VersionStatus.CANARY)

    /**
     * 发布为生效版本（DRAFT/CANARY -> ACTIVE）。
     * 同一 ruleKey 下旧 ACTIVE 版本归档为 ARCHIVED 的动作属于应用层（一次发布涉及两个版本）。
     */
    fun publish(): RuleVersion = transitionTo(VersionStatus.ACTIVE)

    /** 归档（ACTIVE -> ARCHIVED，终态） */
    fun archive(): RuleVersion = transitionTo(VersionStatus.ARCHIVED)

    private fun transitionTo(target: VersionStatus): RuleVersion {
        VersionStatus.transition(status, target)
        return copy(status = target)
    }
}
