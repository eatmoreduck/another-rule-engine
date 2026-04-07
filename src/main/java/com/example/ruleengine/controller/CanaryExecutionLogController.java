package com.example.ruleengine.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.ruleengine.domain.CanaryExecutionLog;
import com.example.ruleengine.service.grayscale.CanaryExecutionLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 灰度执行日志 REST API
 */
@RestController
@RequestMapping("/api/v1/canary-logs")
@RequiredArgsConstructor
@Slf4j
@SaCheckLogin
public class CanaryExecutionLogController {

    private final CanaryExecutionLogService canaryExecutionLogService;

    /**
     * 查询灰度执行日志
     * GET /api/v1/canary-logs
     */
    @GetMapping
    @SaCheckPermission("api:grayscale:view")
    public ResponseEntity<List<CanaryExecutionLog>> queryLogs(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        log.info("查询灰度执行日志: targetType={}, targetKey={}", targetType, targetKey);

        if (targetType == null || targetKey == null) {
            return ResponseEntity.badRequest().build();
        }

        List<CanaryExecutionLog> logs = canaryExecutionLogService.queryLogs(
                targetType, targetKey, startTime, endTime);
        return ResponseEntity.ok(logs);
    }
}
