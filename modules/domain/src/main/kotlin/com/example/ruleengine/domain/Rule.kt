package com.example.ruleengine.domain

import java.time.Instant

/**
 * 规则聚合根。
 *
 * 对应旧实体 Rule（rules 表），提取业务概念、剔除持久化痕迹：
 * - 剔除 JPA 注解、@Version 乐观锁（optLockVersion）、Hibernate 审计时间戳注解
 * - 剔除冗余的 groovyScript 主表列：规则定义载荷收敛到 [RuleVersion]，本聚合只持有版本指针
 *   （currentVersion：最新内容版本号；activeVersion：当前生效版本号）
 *
 * 不变式：
 * - ruleKey / ruleName / createdBy 非空白
 * - currentVersion >= 1
 * - activeVersion 为空或落在 1..currentVersion 区间
 *
 * 所有变更操作均为纯函数（返回新实例），状态迁移经 [RuleStatus.transition] 严格校验。
 */
data class Rule(
    val id: Long? = null,
    val ruleKey: String,
    val ruleName: String,
    val ruleDescription: String? = null,
    val status: RuleStatus = RuleStatus.ENABLED,
    val currentVersion: Int = 1,
    val activeVersion: Int? = null,
    val environmentId: Long? = null,
    /** 所属团队，null 表示全局资源（所有人可见），语义沿用旧模型 */
    val teamId: Long? = null,
    val createdBy: String,
    val createdAt: Instant,
    val updatedBy: String? = null,
    val updatedAt: Instant? = null,
) {
    init {
        require(ruleKey.isNotBlank()) { "ruleKey 不能为空白" }
        require(ruleName.isNotBlank()) { "ruleName 不能为空白" }
        require(createdBy.isNotBlank()) { "createdBy 不能为空白" }
        require(currentVersion >= 1) { "currentVersion 必须 >= 1，实际: $currentVersion" }
        require(activeVersion == null || activeVersion in 1..currentVersion) {
            "activeVersion 必须为空或落在 1..currentVersion($currentVersion) 区间，实际: $activeVersion"
        }
    }

    /** 启用规则（DISABLED -> ENABLED，对应旧 RULE_ENABLE 操作） */
    fun enable(
        operator: String,
        at: Instant,
    ): Rule = copyWithStatus(RuleStatus.ENABLED, operator, at)

    /** 停用规则（ENABLED -> DISABLED，对应旧 RULE_DISABLE 操作） */
    fun disable(
        operator: String,
        at: Instant,
    ): Rule = copyWithStatus(RuleStatus.DISABLED, operator, at)

    /** 软删除规则（ENABLED/DISABLED -> DELETED，终态） */
    fun softDelete(
        operator: String,
        at: Instant,
    ): Rule = copyWithStatus(RuleStatus.DELETED, operator, at)

    /**
     * 发布版本：将 [version] 指定为当前生效版本（对应旧 publishVersion 更新主表动作）。
     *
     * @throws IllegalArgumentException 版本号不在合法区间
     */
    fun activateVersion(
        version: Int,
        operator: String,
        at: Instant,
    ): Rule {
        require(version in 1..currentVersion) {
            "待生效版本 $version 不在 1..currentVersion($currentVersion) 区间"
        }
        return copy(activeVersion = version, updatedBy = operator, updatedAt = at)
    }

    /**
     * 推进最新内容版本号（创建新草稿版本时调用，对应旧 createDraft 的 version = current + 1）。
     */
    fun bumpCurrentVersion(
        operator: String,
        at: Instant,
    ): Rule = copy(currentVersion = currentVersion + 1, updatedBy = operator, updatedAt = at)

    /**
     * 更新名称与描述（对应旧 UpdateRuleRequest 的元信息修改，不含定义载荷）。
     */
    fun rename(
        ruleName: String = this.ruleName,
        ruleDescription: String? = this.ruleDescription,
        operator: String,
        at: Instant,
    ): Rule {
        require(ruleName.isNotBlank()) { "ruleName 不能为空白" }
        return copy(ruleName = ruleName, ruleDescription = ruleDescription, updatedBy = operator, updatedAt = at)
    }

    private fun copyWithStatus(
        target: RuleStatus,
        operator: String,
        at: Instant,
    ): Rule {
        RuleStatus.transition(status, target)
        return copy(status = target, updatedBy = operator, updatedAt = at)
    }
}
