package com.example.ruleengine.admin.dto

import jakarta.validation.constraints.NotBlank

/**
 * 创建名单条目请求（字段与校验消息与旧 CreateNameListEntryRequest 逐字一致）。
 *
 * listKey 可选：默认 GLOBAL（全局共享），可传决策流 flowKey 做流级隔离；
 * expiredAt 为 ISO-8601 本地日期时间字符串（旧契约即字符串，由服务层解析）。
 */
data class CreateNameListEntryRequest(
    @field:NotBlank(message = "名单类型不能为空")
    val listType: String = "",
    val listKey: String? = null,
    @field:NotBlank(message = "键类型不能为空")
    val keyType: String = "",
    @field:NotBlank(message = "键值不能为空")
    val keyValue: String = "",
    val reason: String? = null,
    val source: String? = null,
    val expiredAt: String? = null,
)

/**
 * 名单条目响应体：字段集与旧 JPA 实体 NameListEntry 的序列化形态逐字一致
 * （前端 frontend/src/api/nameList.ts 的 NameListEntry 接口是最终裁判）。
 */
data class NameListEntryResponse(
    val id: Long,
    val listType: String,
    val keyType: String,
    val keyValue: String,
    val listKey: String,
    val reason: String?,
    val source: String?,
    val expiredAt: java.time.Instant?,
    val createdBy: String,
    val createdAt: java.time.Instant,
    val updatedBy: String?,
    val updatedAt: java.time.Instant?,
)

/**
 * 名单批量导入结果（旧契约未暴露导入端点；本方法为服务层能力，
 * 供后续 ImportExport 批次复用，逐条复用单条创建的解析语义）。
 */
data class NameListImportResult(
    val importedCount: Int,
    val skippedCount: Int,
    /** 被跳过条目的原因描述（重复键 / 解析失败），按输入顺序 */
    val skipReasons: List<String>,
)
