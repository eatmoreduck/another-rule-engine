package com.example.ruleengine.controller;

import com.example.ruleengine.model.dto.DependencyGraph;
import com.example.ruleengine.model.dto.RuleAnalytics;
import com.example.ruleengine.service.analytics.RuleAnalyticsService;
import com.example.ruleengine.service.analytics.RuleDependencyAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 规则效果分析 REST API 控制器
 * MON-03, MON-04: 提供规则分析接口
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final RuleAnalyticsService analyticsService;
    private final RuleDependencyAnalyzer dependencyAnalyzer;

    /**
     * 规则效果分析
     * GET /api/v1/analytics/rules/{ruleKey}
     *
     * @param ruleKey   规则Key
     * @param startDate 开始日期（默认最近7天）
     * @param endDate   结束日期（默认今天）
     */
    @GetMapping("/rules/{ruleKey}")
    public ResponseEntity<RuleAnalytics> getRuleAnalytics(
            @PathVariable String ruleKey,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        if (startDate == null) {
            startDate = LocalDate.now().minusDays(7);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        log.info("获取规则效果分析: ruleKey={}, startDate={}, endDate={}",
            ruleKey, startDate, endDate);

        RuleAnalytics analytics = analyticsService.getAnalytics(ruleKey, startDate, endDate);
        return ResponseEntity.ok(analytics);
    }

    /**
     * 全局概览
     * GET /api/v1/analytics/overview
     */
    @GetMapping("/overview")
    public ResponseEntity<List<RuleAnalytics>> getOverview(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        if (startDate == null) {
            startDate = LocalDate.now().minusDays(7);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        log.info("获取全局分析概览: startDate={}, endDate={}", startDate, endDate);
        List<RuleAnalytics> overview = analyticsService.getOverview(startDate, endDate);
        return ResponseEntity.ok(overview);
    }

    /**
     * 依赖关系分析
     * GET /api/v1/analytics/dependencies
     */
    @GetMapping("/dependencies")
    public ResponseEntity<DependencyGraph> getDependencyGraph() {
        log.info("获取规则依赖关系图");
        DependencyGraph graph = dependencyAnalyzer.analyzeDependencies();
        return ResponseEntity.ok(graph);
    }

    /**
     * 单规则依赖关系分析
     * GET /api/v1/analytics/dependencies/{ruleKey}
     */
    @GetMapping("/dependencies/{ruleKey}")
    public ResponseEntity<DependencyGraph> getRuleDependencies(
            @PathVariable String ruleKey) {
        log.info("获取规则依赖关系: ruleKey={}", ruleKey);
        DependencyGraph graph = dependencyAnalyzer.analyzeRuleDependencies(ruleKey);
        return ResponseEntity.ok(graph);
    }
}
