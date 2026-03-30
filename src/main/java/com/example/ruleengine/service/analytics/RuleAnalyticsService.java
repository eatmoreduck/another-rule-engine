package com.example.ruleengine.service.analytics;

import com.example.ruleengine.domain.ExecutionLog;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.model.dto.RuleAnalytics;
import com.example.ruleengine.model.dto.RuleAnalytics.TrendDataPoint;
import com.example.ruleengine.repository.ExecutionLogRepository;
import com.example.ruleengine.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 规则效果分析服务
 * MON-03: 统计命中率、误判率、拦截率
 *
 * 功能：
 * 1. 获取单个规则的效果分析数据
 * 2. 获取全局分析概览
 * 3. 支持按时间段趋势分析
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleAnalyticsService {

    private final ExecutionLogRepository executionLogRepository;
    private final RuleRepository ruleRepository;

    /**
     * 获取规则效果分析数据
     * MON-03: RuleAnalytics getAnalytics(ruleKey, timeRange)
     *
     * @param ruleKey   规则Key
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 规则分析数据
     */
    public RuleAnalytics getAnalytics(String ruleKey, LocalDate startDate, LocalDate endDate) {
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atTime(LocalTime.MAX);

        List<ExecutionLog> logs = executionLogRepository
            .findByRuleKeyAndCreatedAtBetweenOrderByCreatedAtDesc(ruleKey, startTime, endTime);

        Optional<Rule> ruleOpt = ruleRepository.findByRuleKey(ruleKey);
        String ruleName = ruleOpt.map(Rule::getRuleName).orElse(ruleKey);

        return buildAnalytics(ruleKey, ruleName, logs, startDate, endDate);
    }

    /**
     * 获取全局分析概览
     * MON-03: 所有规则的综合分析数据
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 分析数据列表
     */
    public List<RuleAnalytics> getOverview(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atTime(LocalTime.MAX);

        List<ExecutionLog> allLogs = executionLogRepository
            .findByCreatedAtBetweenOrderByCreatedAtDesc(startTime, endTime);

        // 按 ruleKey 分组
        Map<String, List<ExecutionLog>> groupedLogs = allLogs.stream()
            .collect(Collectors.groupingBy(ExecutionLog::getRuleKey));

        List<RuleAnalytics> result = new ArrayList<>();
        for (Map.Entry<String, List<ExecutionLog>> entry : groupedLogs.entrySet()) {
            String ruleKey = entry.getKey();
            Optional<Rule> ruleOpt = ruleRepository.findByRuleKey(ruleKey);
            String ruleName = ruleOpt.map(Rule::getRuleName).orElse(ruleKey);

            RuleAnalytics analytics = buildAnalytics(
                ruleKey, ruleName, entry.getValue(), startDate, endDate);
            result.add(analytics);
        }

        return result;
    }

    /**
     * 构建规则分析数据
     */
    private RuleAnalytics buildAnalytics(String ruleKey, String ruleName,
                                          List<ExecutionLog> logs,
                                          LocalDate startDate, LocalDate endDate) {
        RuleAnalytics analytics = new RuleAnalytics();
        analytics.setRuleKey(ruleKey);
        analytics.setRuleName(ruleName);

        long totalExecutions = logs.size();
        long hitCount = logs.stream()
            .filter(log -> "SUCCESS".equals(log.getStatus()))
            .count();
        long rejectCount = logs.stream()
            .filter(log -> "REJECT".equals(log.getOutputDecision()))
            .count();
        long passCount = logs.stream()
            .filter(log -> "PASS".equals(log.getOutputDecision()))
            .count();
        long errorCount = logs.stream()
            .filter(log -> "ERROR".equals(log.getStatus())
                || "TIMEOUT".equals(log.getStatus()))
            .count();

        analytics.setTotalExecutions(totalExecutions);
        analytics.setHitCount(hitCount);
        analytics.setHitRate(totalExecutions > 0
            ? (double) hitCount / totalExecutions * 100 : 0);
        analytics.setRejectCount(rejectCount);
        analytics.setRejectRate(totalExecutions > 0
            ? (double) rejectCount / totalExecutions * 100 : 0);
        analytics.setPassCount(passCount);
        analytics.setPassRate(totalExecutions > 0
            ? (double) passCount / totalExecutions * 100 : 0);
        analytics.setErrorCount(errorCount);
        analytics.setErrorRate(totalExecutions > 0
            ? (double) errorCount / totalExecutions * 100 : 0);

        // 执行时间统计
        List<Integer> executionTimes = logs.stream()
            .map(ExecutionLog::getExecutionTimeMs)
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toList());

        if (!executionTimes.isEmpty()) {
            analytics.setAvgExecutionTimeMs(
                executionTimes.stream().mapToInt(Integer::intValue).average().orElse(0));
            analytics.setMaxExecutionTimeMs(
                executionTimes.get(executionTimes.size() - 1));
            analytics.setP99ExecutionTimeMs(
                getP99(executionTimes));
        } else {
            analytics.setAvgExecutionTimeMs(0);
            analytics.setMaxExecutionTimeMs(0);
            analytics.setP99ExecutionTimeMs(0);
        }

        // 按天趋势数据
        analytics.setTrendData(buildTrendData(logs, startDate, endDate));

        return analytics;
    }

    /**
     * 构建趋势数据
     */
    private List<TrendDataPoint> buildTrendData(List<ExecutionLog> logs,
                                                  LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, List<ExecutionLog>> byDate = logs.stream()
            .collect(Collectors.groupingBy(log ->
                log.getCreatedAt().toLocalDate()));

        List<TrendDataPoint> trendData = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<ExecutionLog> dayLogs = byDate.getOrDefault(date, Collections.emptyList());

            long dayTotal = dayLogs.size();
            long dayHits = dayLogs.stream()
                .filter(log -> "SUCCESS".equals(log.getStatus()))
                .count();
            double dayHitRate = dayTotal > 0 ? (double) dayHits / dayTotal * 100 : 0;
            double dayAvgTime = dayLogs.stream()
                .map(ExecutionLog::getExecutionTimeMs)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average().orElse(0);

            trendData.add(new TrendDataPoint(
                date.toString(), dayTotal, dayHits, dayHitRate, dayAvgTime));
        }

        return trendData;
    }

    /**
     * 计算 P99 执行时间
     */
    private double getP99(List<Integer> sortedTimes) {
        if (sortedTimes.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(sortedTimes.size() * 0.99) - 1;
        return sortedTimes.get(Math.max(0, Math.min(index, sortedTimes.size() - 1)));
    }
}
