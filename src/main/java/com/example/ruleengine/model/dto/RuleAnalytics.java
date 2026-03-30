package com.example.ruleengine.model.dto;

import java.util.List;
import java.util.Map;

/**
 * 规则效果分析数据
 * MON-03: 统计命中率、误判率、拦截率
 */
public class RuleAnalytics {

    private String ruleKey;
    private String ruleName;
    private long totalExecutions;
    private long hitCount;
    private double hitRate;
    private long rejectCount;
    private double rejectRate;
    private long passCount;
    private double passRate;
    private long errorCount;
    private double errorRate;
    private double avgExecutionTimeMs;
    private double maxExecutionTimeMs;
    private double p99ExecutionTimeMs;
    private List<TrendDataPoint> trendData;

    public RuleAnalytics() {}

    public String getRuleKey() {
        return ruleKey;
    }

    public void setRuleKey(String ruleKey) {
        this.ruleKey = ruleKey;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
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

    public double getHitRate() {
        return hitRate;
    }

    public void setHitRate(double hitRate) {
        this.hitRate = hitRate;
    }

    public long getRejectCount() {
        return rejectCount;
    }

    public void setRejectCount(long rejectCount) {
        this.rejectCount = rejectCount;
    }

    public double getRejectRate() {
        return rejectRate;
    }

    public void setRejectRate(double rejectRate) {
        this.rejectRate = rejectRate;
    }

    public long getPassCount() {
        return passCount;
    }

    public void setPassCount(long passCount) {
        this.passCount = passCount;
    }

    public double getPassRate() {
        return passRate;
    }

    public void setPassRate(double passRate) {
        this.passRate = passRate;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(long errorCount) {
        this.errorCount = errorCount;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(double errorRate) {
        this.errorRate = errorRate;
    }

    public double getAvgExecutionTimeMs() {
        return avgExecutionTimeMs;
    }

    public void setAvgExecutionTimeMs(double avgExecutionTimeMs) {
        this.avgExecutionTimeMs = avgExecutionTimeMs;
    }

    public double getMaxExecutionTimeMs() {
        return maxExecutionTimeMs;
    }

    public void setMaxExecutionTimeMs(double maxExecutionTimeMs) {
        this.maxExecutionTimeMs = maxExecutionTimeMs;
    }

    public double getP99ExecutionTimeMs() {
        return p99ExecutionTimeMs;
    }

    public void setP99ExecutionTimeMs(double p99ExecutionTimeMs) {
        this.p99ExecutionTimeMs = p99ExecutionTimeMs;
    }

    public List<TrendDataPoint> getTrendData() {
        return trendData;
    }

    public void setTrendData(List<TrendDataPoint> trendData) {
        this.trendData = trendData;
    }

    /**
     * 趋势数据点
     */
    public static class TrendDataPoint {
        private String date;
        private long executions;
        private long hits;
        private double hitRate;
        private double avgExecutionTimeMs;

        public TrendDataPoint() {}

        public TrendDataPoint(String date, long executions, long hits,
                              double hitRate, double avgExecutionTimeMs) {
            this.date = date;
            this.executions = executions;
            this.hits = hits;
            this.hitRate = hitRate;
            this.avgExecutionTimeMs = avgExecutionTimeMs;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public long getExecutions() {
            return executions;
        }

        public void setExecutions(long executions) {
            this.executions = executions;
        }

        public long getHits() {
            return hits;
        }

        public void setHits(long hits) {
            this.hits = hits;
        }

        public double getHitRate() {
            return hitRate;
        }

        public void setHitRate(double hitRate) {
            this.hitRate = hitRate;
        }

        public double getAvgExecutionTimeMs() {
            return avgExecutionTimeMs;
        }

        public void setAvgExecutionTimeMs(double avgExecutionTimeMs) {
            this.avgExecutionTimeMs = avgExecutionTimeMs;
        }
    }
}
