package com.example.ruleengine.service.executionlog;

import com.example.ruleengine.domain.ExecutionLog;
import com.example.ruleengine.repository.ExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 规则执行日志服务
 *
 * 功能：
 * 1. 异步记录规则执行日志（输入、输出、执行时间）
 * 2. 查询日志（按规则、时间范围、状态）
 * 3. 定期清理超过 N 天的日志（保留策略）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionLogService {

    private final ExecutionLogRepository executionLogRepository;

    @Value("${rule-engine.execution-log.retention-days:30}")
    private int retentionDays;

    /**
     * 异步记录执行成功日志
     *
     * @param ruleKey 规则Key
     * @param ruleVersion 规则版本
     * @param inputFeatures 输入特征
     * @param outputDecision 决策结果
     * @param outputReason 决策原因
     * @param executionTimeMs 执行耗时（毫秒）
     */
    @Async
    public void logSuccess(String ruleKey, Integer ruleVersion,
                           Map<String, Object> inputFeatures,
                           String outputDecision, String outputReason,
                           long executionTimeMs) {
        try {
            ExecutionLog executionLog = ExecutionLog.builder()
                    .ruleKey(ruleKey)
                    .ruleVersion(ruleVersion)
                    .inputFeatures(inputFeatures)
                    .outputDecision(outputDecision)
                    .outputReason(outputReason)
                    .executionTimeMs((int) executionTimeMs)
                    .status("SUCCESS")
                    .build();
            executionLogRepository.save(executionLog);
        } catch (Exception e) {
            log.error("记录执行日志失败: ruleKey={}", ruleKey, e);
        }
    }

    /**
     * 异步记录执行超时日志
     *
     * @param ruleKey 规则Key
     * @param ruleVersion 规则版本
     * @param inputFeatures 输入特征
     * @param executionTimeMs 执行耗时（毫秒）
     */
    @Async
    public void logTimeout(String ruleKey, Integer ruleVersion,
                           Map<String, Object> inputFeatures,
                           long executionTimeMs) {
        try {
            ExecutionLog executionLog = ExecutionLog.builder()
                    .ruleKey(ruleKey)
                    .ruleVersion(ruleVersion)
                    .inputFeatures(inputFeatures)
                    .outputDecision("REJECT")
                    .outputReason("规则执行超时")
                    .executionTimeMs((int) executionTimeMs)
                    .status("TIMEOUT")
                    .build();
            executionLogRepository.save(executionLog);
        } catch (Exception e) {
            log.error("记录超时日志失败: ruleKey={}", ruleKey, e);
        }
    }

    /**
     * 异步记录执行错误日志
     *
     * @param ruleKey 规则Key
     * @param ruleVersion 规则版本
     * @param inputFeatures 输入特征
     * @param executionTimeMs 执行耗时（毫秒）
     * @param errorMessage 错误信息
     */
    @Async
    public void logError(String ruleKey, Integer ruleVersion,
                         Map<String, Object> inputFeatures,
                         long executionTimeMs, String errorMessage) {
        try {
            ExecutionLog executionLog = ExecutionLog.builder()
                    .ruleKey(ruleKey)
                    .ruleVersion(ruleVersion)
                    .inputFeatures(inputFeatures)
                    .outputDecision("REJECT")
                    .outputReason("规则执行失败: " + errorMessage)
                    .executionTimeMs((int) executionTimeMs)
                    .status("ERROR")
                    .errorMessage(errorMessage)
                    .build();
            executionLogRepository.save(executionLog);
        } catch (Exception e) {
            log.error("记录错误日志失败: ruleKey={}", ruleKey, e);
        }
    }

    /**
     * 查询指定规则的执行日志
     *
     * @param ruleKey 规则Key
     * @return 执行日志列表（按创建时间倒序）
     */
    public List<ExecutionLog> getLogsByRuleKey(String ruleKey) {
        return executionLogRepository.findByRuleKeyOrderByCreatedAtDesc(ruleKey);
    }

    /**
     * 查询指定规则在时间范围内的执行日志
     *
     * @param ruleKey 规则Key
     * @param start 开始时间
     * @param end 结束时间
     * @return 执行日志列表
     */
    public List<ExecutionLog> getLogsByRuleKeyAndTimeRange(
            String ruleKey, LocalDateTime start, LocalDateTime end) {
        return executionLogRepository.findByRuleKeyAndCreatedAtBetweenOrderByCreatedAtDesc(
                ruleKey, start, end);
    }

    /**
     * 查询指定状态的执行日志
     *
     * @param status 执行状态（SUCCESS/TIMEOUT/ERROR）
     * @return 执行日志列表
     */
    public List<ExecutionLog> getLogsByStatus(String status) {
        return executionLogRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    /**
     * 查询最近执行日志
     *
     * @return 最近100条执行日志
     */
    public List<ExecutionLog> getRecentLogs() {
        return executionLogRepository.findTop100ByOrderByCreatedAtDesc();
    }

    /**
     * 定期清理过期执行日志（每天凌晨3点执行）
     * 清理超过 retentionDays 天的日志
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupExpiredLogs() {
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        int deletedCount = executionLogRepository.deleteByCreatedAtBefore(before);
        log.info("清理过期执行日志完成: 删除 {} 条记录, 保留天数={}", deletedCount, retentionDays);
    }

}
