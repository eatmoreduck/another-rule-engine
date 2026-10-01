package com.eatmoreduck.ruleengine.admin.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/**
 * 创建版本请求（对应旧 CreateVersionRequest）。
 */
data class CreateVersionRequest(
    @field:NotBlank(message = "规则脚本不能为空")
    val groovyScript: String = "",
    val changeReason: String? = null,
)

/**
 * 回滚请求（对应旧 RollbackRequest）。
 */
data class RollbackRequest(
    @field:NotNull(message = "目标版本号不能为空")
    val targetVersion: Int? = null,
    val reason: String? = null,
)

/**
 * 版本响应体：字段集与旧 VersionResponse 逐字一致（前端 frontend/src/api/grayscale.ts
 * 的 VersionOption 与 frontend/src/types/importExport.ts 的 RuleVersionRecord 是消费方）。
 *
 * 与旧契约的已知差异：id / ruleId 在新领域模型中不存在（聚合以 ruleKey 逻辑关联，无代理键），
 * 固定为 null——前端两处类型均不消费这两个字段（VersionOption 未声明，RuleVersionRecord
 * 属于导入导出功能，本批未实现），见汇报「行为差异点」。
 */
data class VersionResponse(
    val id: Long?,
    val ruleId: Long?,
    val ruleKey: String,
    val version: Int,
    val groovyScript: String,
    val changeReason: String?,
    val changedBy: String,
    val changedAt: java.time.Instant,
    val isRollback: Boolean,
    val rollbackFromVersion: Int?,
    val status: String?,
)

/**
 * 版本差异响应（对应旧 VersionDiffResponse）。
 */
data class VersionDiffResponse(
    val ruleKey: String,
    val version1: Int,
    val version2: Int,
    val script1: String,
    val script2: String,
    val diff: String,
)
