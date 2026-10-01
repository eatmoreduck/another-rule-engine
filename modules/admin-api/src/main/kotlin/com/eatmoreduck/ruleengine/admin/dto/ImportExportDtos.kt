package com.eatmoreduck.ruleengine.admin.dto

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 规则导出数据结构（对应旧 RuleExportData：规则主记录 + 版本历史）。
 *
 * 同一结构兼任导出响应体与导入请求体（前端导出文件原样回传导入）：
 * - rule 复用 [RuleResponse]（字段集与旧 JPA 实体序列化形态一致，是导出文件的规则节）；
 * - versions 复用 [VersionResponse]（id / ruleId 恒 null，见该 DTO 注释）。
 */
data class RuleExportData(
    /** 导出格式版本 */
    val formatVersion: String = FORMAT_VERSION,
    /** 导出时间戳（ISO 本地时间，格式与旧实现一致） */
    val exportedAt: String,
    /** 导出人（X-Operator 请求头） */
    val exportedBy: String,
    /** 导出的规则列表 */
    val rules: List<RuleExportRecord>,
) {
    /** 单条规则导出记录 */
    data class RuleExportRecord(
        /** 规则基本信息 */
        val rule: RuleResponse,
        /** 版本历史（版本号降序） */
        val versions: List<VersionResponse>,
    )

    companion object {
        /** 当前导出格式版本（与旧实现一致） */
        const val FORMAT_VERSION = "1.0"

        private val EXPORTED_AT_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        /** 以当前时间构造导出时间戳（与旧 LocalDateTime.now() 的 ISO 格式逐字一致） */
        fun nowExportedAt(): String = EXPORTED_AT_FORMAT.format(LocalDateTime.now())
    }
}

/**
 * 规则导入响应（字段集与旧 ImportRulesResponse 逐字一致）。
 */
data class ImportRulesResponse(
    /** 导入是否成功（存在失败记录时仍为 true，见 failures 明细） */
    val success: Boolean,
    /** 成功导入的规则数量 */
    val importedCount: Int,
    /** 跳过的规则数量（ruleKey 已存在） */
    val skippedCount: Int,
    /** 失败的规则数量 */
    val failedCount: Int,
    /** 失败详情 */
    val failures: List<String>,
    /** 汇总消息 */
    val message: String,
) {
    companion object {
        /** 汇总构造（消息格式与旧实现逐字一致） */
        fun of(
            importedCount: Int,
            skippedCount: Int,
            failedCount: Int,
            failures: List<String>,
        ): ImportRulesResponse =
            ImportRulesResponse(
                success = true,
                importedCount = importedCount,
                skippedCount = skippedCount,
                failedCount = failedCount,
                failures = failures,
                message = "导入完成: 成功 $importedCount, 跳过 $skippedCount, 失败 $failedCount",
            )
    }
}
