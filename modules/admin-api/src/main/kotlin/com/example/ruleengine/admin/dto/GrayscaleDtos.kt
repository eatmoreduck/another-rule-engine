package com.example.ruleengine.admin.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

/**
 * 创建灰度配置请求（对应旧 CreateGrayscaleRequest，字段与默认值逐字一致；
 * 规则与决策流两类目标共用，targetKey 优先于 ruleKey）。
 */
data class CreateGrayscaleRequest(
    /** 规则 Key（向后兼容，targetType=RULE 时使用；有 targetKey 时以 targetKey 为准） */
    val ruleKey: String? = null,
    /** 目标类型：RULE / DECISION_FLOW，默认 RULE */
    val targetType: String = "RULE",
    /** 目标 Key（规则 Key 或决策流 Key），优先于 ruleKey */
    val targetKey: String? = null,
    @field:NotNull(message = "灰度版本号不能为空")
    val grayscaleVersion: Int? = null,
    @field:NotNull(message = "灰度百分比不能为空")
    @field:Min(value = 0, message = "灰度百分比最小为0")
    @field:Max(value = 100, message = "灰度百分比最大为100")
    val grayscalePercentage: Int? = null,
    /** 灰度策略类型：PERCENTAGE / FEATURE / WHITELIST，默认 PERCENTAGE */
    val strategyType: String = "PERCENTAGE",
    /** 特征匹配规则（JSON 数组，strategyType=FEATURE 时必填） */
    val featureRules: String? = null,
    /** 白名单用户ID（逗号分隔，strategyType=WHITELIST 时必填） */
    val whitelistIds: String? = null,
    /** 是否启用双跑对比，默认 false */
    val dualRunEnabled: Boolean = false,
    /** 灰度描述 */
    val description: String? = null,
)

/**
 * 灰度配置响应体：字段集与旧 GrayscaleConfigResponse 逐字一致
 * （前端 frontend/src/types/grayscale.ts 的 GrayscaleRecord 是最终裁判）。
 */
data class GrayscaleConfigResponse(
    val id: Long?,
    val ruleKey: String,
    val targetType: String,
    val targetKey: String?,
    val currentVersion: Int,
    val grayscaleVersion: Int,
    val grayscalePercentage: Int,
    val status: String,
    val statusDescription: String?,
    val strategyType: String,
    val featureRules: String?,
    val whitelistIds: String?,
    val dualRunEnabled: Boolean,
    val description: String?,
    val startedAt: java.time.Instant?,
    val completedAt: java.time.Instant?,
    val createdBy: String?,
    val createdAt: java.time.Instant?,
)

/**
 * 灰度对比报告（对应旧 GrayscaleReportResponse + 嵌套 VersionMetrics）。
 */
data class GrayscaleReportResponse(
    val configId: Long?,
    val ruleKey: String,
    val currentVersion: Int,
    val grayscaleVersion: Int,
    val grayscalePercentage: Int,
    val currentVersionMetrics: VersionMetrics?,
    val grayscaleVersionMetrics: VersionMetrics?,
) {
    data class VersionMetrics(
        val version: Int,
        val executionCount: Int,
        val hitCount: Int,
        val errorCount: Int,
        val avgExecutionTimeMs: Int,
        val errorRate: Double,
        val hitRate: Double,
    )
}
