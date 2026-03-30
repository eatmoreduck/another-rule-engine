package com.example.ruleengine.controller;

import com.example.ruleengine.domain.ExecutionLog;
import com.example.ruleengine.service.executionlog.ExecutionLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 规则执行日志查询 REST API 控制器
 */
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@Slf4j
public class ExecutionLogController {

    private final ExecutionLogService executionLogService;

    /**
     * 查询指定规则的执行日志
     * GET /api/v1/logs/rules/{ruleKey}
     *
     * @param ruleKey 规则Key
     * @param start 开始时间（可选）
     * @param end 结束时间（可选）
     * @return 执行日志列表
     */
    @GetMapping("/rules/{ruleKey}")
    public ResponseEntity<List<ExecutionLog>> getLogsByRuleKey(
            @PathVariable String ruleKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        log.info("查询规则执行日志: ruleKey={}, start={}, end={}", ruleKey, start, end);

        List<ExecutionLog> logs;
        if (start != null && end != null) {
            logs = executionLogService.getLogsByRuleKeyAndTimeRange(ruleKey, start, end);
        } else {
            logs = executionLogService.getLogsByRuleKey(ruleKey);
        }

        return ResponseEntity.ok(logs);
    }

    /**
     * 查询最近执行日志
     * GET /api/v1/logs/recent
     *
     * @return 最近100条执行日志
     */
    @GetMapping("/recent")
    public ResponseEntity<List<ExecutionLog>> getRecentLogs() {
        log.info("查询最近执行日志");
        List<ExecutionLog> logs = executionLogService.getRecentLogs();
        return ResponseEntity.ok(logs);
    }

    /**
     * 按状态查询执行日志
     * GET /api/v1/logs/status/{status}
     *
     * @param status 执行状态（SUCCESS/TIMEOUT/ERROR）
     * @return 执行日志列表
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<ExecutionLog>> getLogsByStatus(@PathVariable String status) {
        log.info("按状态查询执行日志: status={}", status);
        List<ExecutionLog> logs = executionLogService.getLogsByStatus(status);
        return ResponseEntity.ok(logs);
    }
}
