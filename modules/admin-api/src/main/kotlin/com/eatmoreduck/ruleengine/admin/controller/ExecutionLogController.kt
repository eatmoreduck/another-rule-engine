package com.eatmoreduck.ruleengine.admin.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.eatmoreduck.ruleengine.admin.dto.ExecutionLogResponse
import com.eatmoreduck.ruleengine.admin.monitoring.ExecutionLogQueryService
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

/**
 * 规则执行日志查询 REST API 控制器（路径/参数/响应字段与旧 ExecutionLogController 一致：
 * /api/v1/logs 族，类级登录校验，无方法级权限码）。
 */
@RestController
@RequestMapping("/api/v1/logs")
@SaCheckLogin
class ExecutionLogController(
    private val executionLogQueryService: ExecutionLogQueryService,
) {
    private val log = LoggerFactory.getLogger(ExecutionLogController::class.java)

    /** 查询指定规则的执行日志：GET /api/v1/logs/rules/{ruleKey}?start=&end= */
    @GetMapping("/rules/{ruleKey}")
    fun getLogsByRuleKey(
        @PathVariable ruleKey: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) start: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) end: LocalDateTime?,
    ): ResponseEntity<List<ExecutionLogResponse>> {
        log.info("查询规则执行日志: ruleKey={}, start={}, end={}", ruleKey, start, end)
        return ResponseEntity.ok(executionLogQueryService.getLogsByRuleKey(ruleKey, start, end))
    }

    /** 查询最近执行日志：GET /api/v1/logs/recent?limit=20&level= */
    @GetMapping("/recent")
    fun getRecentLogs(
        @RequestParam(required = false, defaultValue = "20") limit: Int,
        @RequestParam(required = false) level: String?,
    ): ResponseEntity<List<ExecutionLogResponse>> {
        log.info("查询最近执行日志: limit={}, level={}", limit, level)
        return ResponseEntity.ok(executionLogQueryService.getRecentLogs(limit, level))
    }

    /** 按状态查询执行日志：GET /api/v1/logs/status/{status} */
    @GetMapping("/status/{status}")
    fun getLogsByStatus(
        @PathVariable status: String,
    ): ResponseEntity<List<ExecutionLogResponse>> {
        log.info("按状态查询执行日志: status={}", status)
        return ResponseEntity.ok(executionLogQueryService.getLogsByStatus(status))
    }
}
