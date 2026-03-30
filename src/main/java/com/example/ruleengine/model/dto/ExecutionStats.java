package com.example.ruleengine.model.dto;

import java.time.Instant;

/**
 * 规则执行统计 DTO
 * MON-01: 系统记录规则命中统计（执行次数、命中次数）
 */
public class ExecutionStats {

    private long totalExecutions;
    private long hitCount;
    private long errorCount;
    private double avgExecutionTimeMs;
    private double p95ExecutionTimeMs;
    private Instant lastExecutedAt;

    public ExecutionStats() {}

    public ExecutionStats(long totalExecutions, long hitCount, long errorCount,
                          double avgExecutionTimeMs, double p95ExecutionTimeMs,
                          Instant lastExecutedAt) {
        this.totalExecutions = totalExecutions;
        this.hitCount = hitCount;
        this.errorCount = errorCount;
        this.avgExecutionTimeMs = avgExecutionTimeMs;
        this.p95ExecutionTimeMs = p95ExecutionTimeMs;
        this.lastExecutedAt = lastExecutedAt;
    }

    public long getTotalExecutions() {
        return totalExecutions;
    }

    public void setTotalExecutions(long totalExecutions) {
        this.totalExecutions = totalExecutions;
    }

    public long getHitCount() {
        return hitCount;
    }

    public void setHitCount(long hitCount) {
        this.hitCount = hitCount;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(long errorCount) {
        this.errorCount = errorCount;
    }

    public double getAvgExecutionTimeMs() {
        return avgExecutionTimeMs;
    }

    public void setAvgExecutionTimeMs(double avgExecutionTimeMs) {
        this.avgExecutionTimeMs = avgExecutionTimeMs;
    }

    public double getP95ExecutionTimeMs() {
        return p95ExecutionTimeMs;
    }

    public void setP95ExecutionTimeMs(double p95ExecutionTimeMs) {
        this.p95ExecutionTimeMs = p95ExecutionTimeMs;
    }

    public Instant getLastExecutedAt() {
        return lastExecutedAt;
    }

    public void setLastExecutedAt(Instant lastExecutedAt) {
        this.lastExecutedAt = lastExecutedAt;
    }
}
