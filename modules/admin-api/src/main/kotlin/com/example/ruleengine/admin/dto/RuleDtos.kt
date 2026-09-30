package com.example.ruleengine.admin.dto

import jakarta.validation.constraints.NotBlank

/**
 * 创建规则请求（字段与校验消息与旧 CreateRuleRequest 逐字一致）。
 */
data class CreateRuleRequest(
    @field:NotBlank(message = "规则Key不能为空")
    val ruleKey: String = "",
    @field:NotBlank(message = "规则名称不能为空")
    val ruleName: String = "",
    val ruleDescription: String? = null,
    @field:NotBlank(message = "Groovy脚本不能为空")
    val groovyScript: String = "",
)

/**
 * 更新规则请求（对应旧 UpdateRuleRequest；全部字段可选，脚本为空表示仅更新元数据）。
 */
data class UpdateRuleRequest(
    val ruleName: String? = null,
    val ruleDescription: String? = null,
    val groovyScript: String? = null,
    val changeReason: String? = null,
)

/**
 * 规则查询对象（对应旧 RuleQuery，多条件过滤）。
 */
data class RuleQuery(
    val createdBy: String? = null,
    val enabled: Boolean? = null,
    val keyword: String? = null,
    val createdAtStart: java.time.LocalDateTime? = null,
    val createdAtEnd: java.time.LocalDateTime? = null,
    val updatedAtStart: java.time.LocalDateTime? = null,
    val updatedAtEnd: java.time.LocalDateTime? = null,
)

/**
 * 规则响应体：字段集与旧 JPA 实体 Rule 的序列化形态逐字一致
 * （前端 frontend/src/types/rule.ts 的 Rule 接口是最终裁判）。
 *
 * 组装来源（阶段 1 领域模型）：规则主数据来自 domain Rule，脚本载荷来自当前版本的
 * RuleVersion.definitionJson（旧主表 groovy_script 冗余列在新架构中已废弃）；
 * enabled/deleted 由 RuleStatus 三态展开，optLockVersion 为旧 JPA @Version 遗留列，恒 0。
 */
data class RuleResponse(
    val id: Long?,
    val ruleKey: String,
    val ruleName: String,
    val ruleDescription: String?,
    val groovyScript: String,
    val version: Int,
    val createdBy: String,
    val createdAt: java.time.Instant?,
    val updatedBy: String?,
    val updatedAt: java.time.Instant?,
    val enabled: Boolean,
    val deleted: Boolean,
    val optLockVersion: Long?,
    val activeVersion: Int?,
    val environmentId: Long?,
    val teamId: Long?,
)

/**
 * 脚本验证请求（对应旧 ValidateScriptRequest）。
 */
data class ValidateScriptRequest(
    @field:NotBlank(message = "Groovy脚本不能为空")
    val groovyScript: String = "",
)

/**
 * 脚本验证响应（对应旧 ValidateScriptResponse：valid / errorMessage / errorDetails）。
 */
data class ValidateScriptResponse(
    val valid: Boolean,
    val errorMessage: String? = null,
    val errorDetails: String? = null,
) {
    companion object {
        fun success(): ValidateScriptResponse = ValidateScriptResponse(valid = true)

        fun error(
            errorMessage: String,
            errorDetails: String,
        ): ValidateScriptResponse =
            ValidateScriptResponse(
                valid = false,
                errorMessage = errorMessage,
                errorDetails = errorDetails,
            )
    }
}

/**
 * 规则引用关系（对应旧 RuleReference：引用方为决策流或规则集）。
 */
data class RuleReferenceResponse(
    val type: String,
    val id: Long?,
    val name: String?,
    val key: String?,
)
