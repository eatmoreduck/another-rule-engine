package com.example.ruleengine.metrics;

import com.example.ruleengine.model.dto.ExecutionStats;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 规则执行监控指标
 * MON-01: 系统记录规则命中统计（执行次数、命中次数）
 *
 * 使用 Micrometer Counter/Timer 记录：
 * - rule.execution.total (按 ruleId 标签) - 执行总次数
 * - rule.execution.hit (按 ruleId 标签) - 命中次数
 * - rule.execution.time (按 ruleId 标签) - 执行耗时
 * - rule.execution.error (按 ruleId 标签) - 错误次数
 */
@Component
public class RuleExecutionMetrics {

    private static final String METRIC_EXECUTION_TOTAL = "rule.execution.total";
    private static final String METRIC_EXECUTION_HIT = "rule.execution.hit";
    private static final String METRIC_EXECUTION_TIME = "rule.execution.time";
    private static final String METRIC_EXECUTION_ERROR = "rule.execution.error";
    private static final String TAG_RULE_ID = "ruleId";

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Instant> lastExecutionTimeMap = new ConcurrentHashMap<>();

    public RuleExecutionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 记录规则执行结果
     *
     * @param ruleKey    规则标识
     * @param decision   决策结果（"PASS" 表示命中）
     * @param durationMs 执行耗时（毫秒）
     */
    public void recordExecution(String ruleKey, String decision, long durationMs) {
        // 记录执行总次数
        Counter.builder(METRIC_EXECUTION_TOTAL)
            .tag(TAG_RULE_ID, ruleKey)
            .description("Total number of rule executions")
            .register(meterRegistry)
            .increment();

        // 记录命中次数（decision 为 PASS 时视为命中）
        if ("PASS".equalsIgnoreCase(decision)) {
            Counter.builder(METRIC_EXECUTION_HIT)
                .tag(TAG_RULE_ID, ruleKey)
                .description("Number of rule hits")
                .register(meterRegistry)
                .increment();
        }

        // 记录执行耗时
        Timer.builder(METRIC_EXECUTION_TIME)
            .tag(TAG_RULE_ID, ruleKey)
            .description("Rule execution time")
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);

        // 更新最后执行时间
        lastExecutionTimeMap.put(ruleKey, Instant.now());
    }

    /**
     * 记录规则执行错误
     *
     * @param ruleKey   规则标识
     * @param exception 异常信息
     */
    public void recordError(String ruleKey, Exception exception) {
        Counter errorCounter = Counter.builder(METRIC_EXECUTION_ERROR)
            .tag(TAG_RULE_ID, ruleKey)
            .description("Number of rule execution errors")
            .register(meterRegistry);

        errorCounter.increment();
    }

    /**
     * 获取指定规则的执行统计
     *
     * @param ruleKey 规则标识
     * @return 执行统计信息
     */
    public ExecutionStats getExecutionStats(String ruleKey) {
        // 查找各指标
        Counter totalCounter = findCounter(METRIC_EXECUTION_TOTAL, ruleKey);
        Counter hitCounter = findCounter(METRIC_EXECUTION_HIT, ruleKey);
        Counter errorCounter = findCounter(METRIC_EXECUTION_ERROR, ruleKey);
        Timer executionTimer = findTimer(METRIC_EXECUTION_TIME, ruleKey);

        long totalExecutions = totalCounter != null ? (long) totalCounter.count() : 0L;
        long hitCount = hitCounter != null ? (long) hitCounter.count() : 0L;
        long errorCount = errorCounter != null ? (long) errorCounter.count() : 0L;

        double avgExecutionTimeMs = 0.0;
        double p95ExecutionTimeMs = 0.0;
        if (executionTimer != null && executionTimer.count() > 0) {
            avgExecutionTimeMs = executionTimer.mean(TimeUnit.MILLISECONDS);
            p95ExecutionTimeMs = executionTimer.percentile(0.95, TimeUnit.MILLISECONDS);
        }

        Instant lastExecutedAt = lastExecutionTimeMap.get(ruleKey);

        return new ExecutionStats(
            totalExecutions,
            hitCount,
            errorCount,
            avgExecutionTimeMs,
            p95ExecutionTimeMs,
            lastExecutedAt
        );
    }

    /**
     * 查找指定指标名称和 ruleId 标签的 Counter
     */
    private Counter findCounter(String metricName, String ruleKey) {
        return meterRegistry.find(metricName)
            .tag(TAG_RULE_ID, ruleKey)
            .counter();
    }

    /**
     * 查找指定指标名称和 ruleId 标签的 Timer
     */
    private Timer findTimer(String metricName, String ruleKey) {
        return meterRegistry.find(metricName)
            .tag(TAG_RULE_ID, ruleKey)
            .timer();
    }
}
