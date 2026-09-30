package com.example.ruleengine.admin.dto

/**
 * 审计日志响应体：字段集与旧 JPA 实体 AuditLog 的序列化形态逐字一致
 * （消费方：frontend/src/api/system.ts 的 AuditLogDTO，审计页 AuditLogPage）。
 */
data class AuditLogResponse(
    val id: Long,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val operationDetail: String?,
    val operator: String,
    val operatorIp: String?,
    val operationTime: java.time.Instant,
    val status: String,
    val errorMessage: String?,
    val requestId: String?,
)

/**
 * 审计日志分页查询参数（GET /api/v1/audit/logs 的 query string 绑定，
 * 字段与旧 AuditLogController 的 @RequestParam 一致）。
 */
data class AuditLogFilter(
    val operator: String? = null,
    val entityType: String? = null,
    val operation: String? = null,
    val startTime: java.time.LocalDateTime? = null,
    val endTime: java.time.LocalDateTime? = null,
)
