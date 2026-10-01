package com.eatmoreduck.ruleengine.admin.rules

import com.eatmoreduck.ruleengine.admin.dto.RuleResponse
import com.eatmoreduck.ruleengine.admin.dto.VersionResponse
import com.eatmoreduck.ruleengine.domain.Rule
import com.eatmoreduck.ruleengine.domain.RuleStatus
import com.eatmoreduck.ruleengine.domain.RuleVersion
import org.springframework.stereotype.Component

/**
 * 规则域 → API DTO 的组装器：
 * - 规则响应的字段集与旧 JPA 实体序列化形态逐字对齐（groovyScript 由当前版本载荷回填，
 *   enabled/deleted 由 RuleStatus 三态展开）；
 * - 版本响应的字段集与旧 VersionResponse 逐字对齐。
 */
@Component
class RuleAssembler {
    /**
     * 组装规则响应。
     *
     * @param currentPayload 当前版本的定义载荷（rule_versions 中版本号最大者）；
     *   新架构下主表 groovy_script 已废弃（新写入为占位空串），响应中的脚本文本以此为准
     */
    fun toRuleResponse(
        rule: Rule,
        currentPayload: String?,
    ): RuleResponse =
        RuleResponse(
            id = rule.id,
            ruleKey = rule.ruleKey,
            ruleName = rule.ruleName,
            ruleDescription = rule.ruleDescription,
            groovyScript = currentPayload ?: "",
            version = rule.currentVersion,
            createdBy = rule.createdBy,
            createdAt = rule.createdAt,
            updatedBy = rule.updatedBy,
            updatedAt = rule.updatedAt,
            enabled = rule.status == RuleStatus.ENABLED,
            deleted = rule.status == RuleStatus.DELETED,
            optLockVersion = 0L,
            activeVersion = rule.activeVersion,
            environmentId = rule.environmentId,
            teamId = rule.teamId,
        )

    /** 版本行 → 版本响应（id/ruleId 在新领域模型中不存在，恒 null，见 DTO 注释） */
    fun toVersionResponse(version: RuleVersion): VersionResponse =
        VersionResponse(
            id = null,
            ruleId = null,
            ruleKey = version.ruleKey,
            version = version.version,
            groovyScript = version.definitionJson,
            changeReason = version.changeReason,
            changedBy = version.changedBy,
            changedAt = version.changedAt,
            isRollback = version.isRollback,
            rollbackFromVersion = version.rollbackFromVersion,
            status = version.status.name,
        )
}
