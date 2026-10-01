package com.eatmoreduck.ruleengine.admin.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.annotation.SaCheckPermission
import com.eatmoreduck.ruleengine.admin.audit.AuditLogQueryService
import com.eatmoreduck.ruleengine.admin.dto.AuditLogFilter
import com.eatmoreduck.ruleengine.admin.dto.AuditLogResponse
import com.eatmoreduck.ruleengine.admin.dto.PageResponse
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 审计日志查询 REST API 控制器（路径/方法/权限码与旧 AuditLogController 一致；
 * 实体历史与操作人活动仅要求登录，分页综合查询要求 api:system:role:view 权限）。
 */
@RestController
@RequestMapping("/api/v1/audit")
@SaCheckLogin
class AuditLogController(
    private val auditLogService: AuditLogQueryService,
) {
    private val log = LoggerFactory.getLogger(AuditLogController::class.java)

    /** 获取实体审计历史：GET /api/v1/audit/logs/{entityType}/{entityId} */
    @GetMapping("/logs/{entityType}/{entityId}")
    fun getAuditHistory(
        @PathVariable entityType: String,
        @PathVariable entityId: String,
    ): ResponseEntity<List<AuditLogResponse>> {
        log.info("获取实体审计历史: entityType={}, entityId={}", entityType, entityId)
        return ResponseEntity.ok(auditLogService.getAuditHistory(entityType, entityId))
    }

    /** 获取操作人活动记录：GET /api/v1/audit/logs/operator/{operator}?start=xxx&end=xxx */
    @GetMapping("/logs/operator/{operator}")
    fun getOperatorActivity(
        @PathVariable operator: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) start: java.time.LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) end: java.time.LocalDateTime?,
    ): ResponseEntity<List<AuditLogResponse>> {
        log.info("获取操作人活动记录: operator={}, start={}, end={}", operator, start, end)
        return ResponseEntity.ok(auditLogService.getOperatorActivity(operator, start, end))
    }

    /** 分页查询审计日志（多条件过滤）：GET /api/v1/audit/logs */
    @GetMapping("/logs")
    @SaCheckPermission("api:system:role:view")
    fun queryAuditLogs(
        @RequestParam(required = false) operator: String?,
        @RequestParam(required = false) entityType: String?,
        @RequestParam(required = false) operation: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startTime: java.time.LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endTime: java.time.LocalDateTime?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<AuditLogResponse>> {
        log.info("分页查询审计日志: operator={}, entityType={}, operation={}", operator, entityType, operation)
        val filter = AuditLogFilter(operator, entityType, operation, startTime, endTime)
        return ResponseEntity.ok(auditLogService.queryAuditLogs(filter, page, size))
    }
}
