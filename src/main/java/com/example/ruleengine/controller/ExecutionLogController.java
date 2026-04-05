package com.example.ruleengine.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.ruleengine.domain.ExecutionLog;
import com.example.ruleengine.model.dto.ExecutionLogResponse;
import com.example.ruleengine.service.executionlog.ExecutionLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 规则执行日志查询 REST API 控制器
 */
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@Slf4j
@SaCheckLogin
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
    public ResponseEntity<List<ExecutionLogResponse>> getLogsByRuleKey(
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

        List<ExecutionLogResponse> responses = logs.stream()
                .map(ExecutionLogResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * 查询最近执行日志
     * GET /api/v1/logs/recent
     *
     * @param limit 返回数量限制（默认20）
     * @param level 日志级别过滤（可选）
     * @return 最近执行日志
     */
    @GetMapping("/recent")
    public ResponseEntity<List<ExecutionLogResponse>> getRecentLogs(
            @RequestParam(required = false, defaultValue = "20") int limit,
            @RequestParam(required = false) String level) {
        log.info("查询最近执行日志: limit={}, level={}", limit, level);
        List<ExecutionLog> logs = executionLogService.getRecentLogs();

        List<ExecutionLogResponse> responses = logs.stream()
                .map(ExecutionLogResponse::fromEntity)
                .collect(Collectors.toList());

        // 按级别过滤
        if (level != null && !level.isBlank()) {
            responses = responses.stream()
                    .filter(r -> level.equalsIgnoreCase(r.getLevel()))
                    .collect(Collectors.toList());
        }

        // 限制返回数量
        if (limit > 0 && responses.size() > limit) {
            responses = responses.subList(0, limit);
        }

        return ResponseEntity.ok(responses);
    }

    /**
     * 按状态查询执行日志
     * GET /api/v1/logs/status/{status}
     *
     * @param status 执行状态（SUCCESS/TIMEOUT/ERROR）
     * @return 执行日志列表
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<ExecutionLogResponse>> getLogsByStatus(@PathVariable String status) {
        log.info("按状态查询执行日志: status={}", status);
        List<ExecutionLog> logs = executionLogService.getLogsByStatus(status);
        List<ExecutionLogResponse> responses = logs.stream()
                .map(ExecutionLogResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
}
