package com.example.ruleengine.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.ruleengine.domain.AuditLog;
import com.example.ruleengine.service.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志查询 REST API 控制器
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Slf4j
@SaCheckLogin
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 获取实体审计历史
     * GET /api/v1/audit/logs/{entityType}/{entityId}
     */
    @GetMapping("/logs/{entityType}/{entityId}")
    public ResponseEntity<List<AuditLog>> getAuditHistory(
            @PathVariable String entityType,
            @PathVariable String entityId) {
        log.info("获取实体审计历史: entityType={}, entityId={}", entityType, entityId);
        List<AuditLog> logs = auditLogService.getAuditHistory(entityType, entityId);
        return ResponseEntity.ok(logs);
    }

    /**
     * 获取操作人活动记录
     * GET /api/v1/audit/logs/operator/{operator}?start=xxx&end=xxx
     */
    @GetMapping("/logs/operator/{operator}")
    public ResponseEntity<List<AuditLog>> getOperatorActivity(
            @PathVariable String operator,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        log.info("获取操作人活动记录: operator={}, start={}, end={}", operator, start, end);

        if (start == null) {
            start = LocalDateTime.now().minusDays(7);
        }
        if (end == null) {
            end = LocalDateTime.now();
        }

        List<AuditLog> logs = auditLogService.getOperatorActivity(operator, start, end);
        return ResponseEntity.ok(logs);
    }

    /**
     * 分页查询审计日志（支持多条件过滤）
     * GET /api/v1/audit/logs?operator=xxx&entityType=RULE&operation=RULE_CREATE&startTime=xxx&endTime=xxx&page=0&size=20
     */
    @GetMapping("/logs")
    @SaCheckPermission("api:system:role:view")
    public ResponseEntity<Page<AuditLog>> queryAuditLogs(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            Pageable pageable) {
        log.info("分页查询审计日志: operator={}, entityType={}, operation={}", operator, entityType, operation);
        Page<AuditLog> result = auditLogService.queryAuditLogs(
                operator, entityType, operation, startTime, endTime, pageable);
        return ResponseEntity.ok(result);
    }
}
