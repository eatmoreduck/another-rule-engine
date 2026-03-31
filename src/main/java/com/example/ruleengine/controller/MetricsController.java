package com.example.ruleengine.controller;

import com.example.ruleengine.metrics.RuleExecutionMetrics;
import com.example.ruleengine.model.dto.ExecutionStats;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 规则执行监控指标 API
 * MON-01: 系统记录规则命中统计（执行次数、命中次数）
 */
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private static final Logger logger = LoggerFactory.getLogger(MetricsController.class);

    private final RuleExecutionMetrics ruleExecutionMetrics;
    private final MeterRegistry meterRegistry;

    public MetricsController(RuleExecutionMetrics ruleExecutionMetrics,
                             MeterRegistry meterRegistry) {
        this.ruleExecutionMetrics = ruleExecutionMetrics;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 获取监控总览数据
     * GET /api/v1/metrics/overview
     *
     * @return 总执行次数、命中次数、命中率、平均耗时、错误次数、错误率
     */
    @GetMapping(
        value = "/overview",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> getMetricsOverview() {
        logger.debug("获取监控总览数据");

        long totalExecutions = 0;
        long hitCount = 0;
        long errorCount = 0;
        double totalAvgTime = 0.0;
        int timerCount = 0;

        // 汇总所有规则的指标
        Collection<Counter> totalCounters = meterRegistry.find("rule.execution.total").counters();
        for (Counter c : totalCounters) {
            totalExecutions += (long) c.count();
        }

        Collection<Counter> hitCounters = meterRegistry.find("rule.execution.hit").counters();
        for (Counter c : hitCounters) {
            hitCount += (long) c.count();
        }

        Collection<Counter> errorCounters = meterRegistry.find("rule.execution.error").counters();
        for (Counter c : errorCounters) {
            errorCount += (long) c.count();
        }

        Collection<Timer> timers = meterRegistry.find("rule.execution.time").timers();
        for (Timer t : timers) {
            if (t.count() > 0) {
                totalAvgTime += t.mean(TimeUnit.MILLISECONDS);
                timerCount++;
            }
        }

        double avgExecutionTime = timerCount > 0 ? totalAvgTime / timerCount : 0.0;
        double hitRate = totalExecutions > 0 ? (double) hitCount / totalExecutions * 100 : 0.0;
        double errorRate = totalExecutions > 0 ? (double) errorCount / totalExecutions * 100 : 0.0;

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalExecutions", totalExecutions);
        overview.put("hitCount", hitCount);
        overview.put("hitRate", Math.round(hitRate * 100.0) / 100.0);
        overview.put("avgExecutionTime", Math.round(avgExecutionTime * 100.0) / 100.0);
        overview.put("errorCount", errorCount);
        overview.put("errorRate", Math.round(errorRate * 100.0) / 100.0);

        return ResponseEntity.ok(overview);
    }

    /**
     * 获取规则执行排行（按指标排序）
     * GET /api/v1/metrics/rules
     *
     * @param sortBy    排序字段（executionCount/avgExecutionTime/hitRate/errorCount）
     * @param sortOrder 排序方向（asc/desc）
     * @param limit     返回数量限制
     * @return 规则指标列表
     */
    @GetMapping(
        value = "/rules",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<Map<String, Object>>> getRuleMetricsRanking(
            @RequestParam(defaultValue = "executionCount") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(defaultValue = "10") int limit) {
        logger.debug("获取规则执行排行: sortBy={}, sortOrder={}, limit={}", sortBy, sortOrder, limit);

        // 收集所有规则的 ruleId 标签
        Set<String> ruleIds = new HashSet<>();
        meterRegistry.find("rule.execution.total").counters()
            .forEach(c -> {
                String ruleId = c.getId().getTag("ruleId");
                if (ruleId != null) {
                    ruleIds.add(ruleId);
                }
            });
        meterRegistry.find("rule.execution.error").counters()
            .forEach(c -> {
                String ruleId = c.getId().getTag("ruleId");
                if (ruleId != null) {
                    ruleIds.add(ruleId);
                }
            });

        // 构建每条规则的指标数据
        List<Map<String, Object>> metricsList = ruleIds.stream()
            .map(ruleId -> {
                ExecutionStats stats = ruleExecutionMetrics.getExecutionStats(ruleId);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ruleKey", ruleId);
                m.put("ruleName", ruleId);
                m.put("executionCount", stats.getTotalExecutions());
                m.put("hitCount", stats.getHitCount());
                double hitRate = stats.getTotalExecutions() > 0
                    ? (double) stats.getHitCount() / stats.getTotalExecutions() * 100 : 0.0;
                m.put("hitRate", Math.round(hitRate * 100.0) / 100.0);
                m.put("avgExecutionTime", Math.round(stats.getAvgExecutionTimeMs() * 100.0) / 100.0);
                m.put("errorCount", stats.getErrorCount());
                m.put("enabled", true);
                return m;
            })
            .collect(Collectors.toList());

        // 排序
        Comparator<Map<String, Object>> comparator = buildComparator(sortBy);
        if ("desc".equalsIgnoreCase(sortOrder)) {
            comparator = comparator.reversed();
        }
        metricsList.sort(comparator);

        // 限制返回数量
        if (limit > 0 && metricsList.size() > limit) {
            metricsList = metricsList.subList(0, limit);
        }

        return ResponseEntity.ok(metricsList);
    }

    /**
     * 获取指定规则的执行统计
     *
     * @param ruleKey 规则标识
     * @return 执行统计信息
     */
    @GetMapping(
        value = "/rules/{ruleKey}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ExecutionStats> getRuleMetrics(@PathVariable String ruleKey) {
        logger.debug("Fetching execution stats for rule: {}", ruleKey);

        ExecutionStats stats = ruleExecutionMetrics.getExecutionStats(ruleKey);

        return ResponseEntity.ok(stats);
    }

    /**
     * 构建排序比较器
     */
    private Comparator<Map<String, Object>> buildComparator(String sortBy) {
        return switch (sortBy) {
            case "avgExecutionTime" -> Comparator.comparingDouble(
                m -> ((Number) m.getOrDefault("avgExecutionTime", 0)).doubleValue());
            case "hitRate" -> Comparator.comparingDouble(
                m -> ((Number) m.getOrDefault("hitRate", 0)).doubleValue());
            case "errorCount" -> Comparator.comparingLong(
                m -> ((Number) m.getOrDefault("errorCount", 0)).longValue());
            default -> Comparator.comparingLong(
                m -> ((Number) m.getOrDefault("executionCount", 0)).longValue());
        };
    }
}
