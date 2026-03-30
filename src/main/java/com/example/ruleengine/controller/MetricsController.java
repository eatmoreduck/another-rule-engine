package com.example.ruleengine.controller;

import com.example.ruleengine.metrics.RuleExecutionMetrics;
import com.example.ruleengine.model.dto.ExecutionStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 规则执行监控指标 API
 * MON-01: 系统记录规则命中统计（执行次数、命中次数）
 */
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private static final Logger logger = LoggerFactory.getLogger(MetricsController.class);

    private final RuleExecutionMetrics ruleExecutionMetrics;

    public MetricsController(RuleExecutionMetrics ruleExecutionMetrics) {
        this.ruleExecutionMetrics = ruleExecutionMetrics;
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
}
