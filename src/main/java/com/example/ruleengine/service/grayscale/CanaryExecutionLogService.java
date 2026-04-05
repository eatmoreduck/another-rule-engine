package com.example.ruleengine.service.grayscale;

import com.example.ruleengine.domain.CanaryExecutionLog;
import com.example.ruleengine.repository.CanaryExecutionLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 灰度执行日志服务
 * 异步记录灰度执行的每次请求详情
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CanaryExecutionLogService {

    private final CanaryExecutionLogRepository canaryExecutionLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * 异步记录灰度执行日志
     * 使用 @Async 不阻塞主流程
     *
     * @param traceId         追踪ID
     * @param targetType      目标类型（RULE / DECISION_FLOW）
     * @param targetKey       目标 Key
     * @param versionUsed     使用的版本号
     * @param isCanary        是否命中灰度
     * @param requestFeatures 请求特征数据
     * @param decisionResult  决策结果
     * @param executionTimeMs 执行耗时（毫秒）
     */
    @Async
    public void asyncLog(String traceId, String targetType, String targetKey,
                         int versionUsed, boolean isCanary,
                         Map<String, Object> requestFeatures,
                         String decisionResult, long executionTimeMs) {
        try {
            String featuresJson = null;
            if (requestFeatures != null && !requestFeatures.isEmpty()) {
                featuresJson = objectMapper.writeValueAsString(requestFeatures);
            }

            CanaryExecutionLog logEntry = CanaryExecutionLog.builder()
                    .traceId(traceId)
                    .targetType(targetType)
                    .targetKey(targetKey)
                    .versionUsed(versionUsed)
                    .isCanary(isCanary)
                    .requestFeatures(featuresJson)
                    .decisionResult(decisionResult)
                    .executionTimeMs(executionTimeMs)
                    .build();

            canaryExecutionLogRepository.save(logEntry);

            log.debug("灰度执行日志已记录: traceId={}, targetType={}, targetKey={}, isCanary={}, version={}",
                    traceId, targetType, targetKey, isCanary, versionUsed);
        } catch (Exception e) {
            log.error("记录灰度执行日志失败: traceId={}, targetType={}, targetKey={}",
                    traceId, targetType, targetKey, e);
        }
    }

    /**
     * 异步记录灰度执行错误日志
     *
     * @param traceId         追踪ID
     * @param targetType      目标类型
     * @param targetKey       目标 Key
     * @param versionUsed     使用的版本号
     * @param isCanary        是否命中灰度
     * @param requestFeatures 请求特征数据
     * @param errorMessage    错误信息
     * @param executionTimeMs 执行耗时
     */
    @Async
    public void asyncLogError(String traceId, String targetType, String targetKey,
                              int versionUsed, boolean isCanary,
                              Map<String, Object> requestFeatures,
                              String errorMessage, long executionTimeMs) {
        try {
            String featuresJson = null;
            if (requestFeatures != null && !requestFeatures.isEmpty()) {
                featuresJson = objectMapper.writeValueAsString(requestFeatures);
            }

            CanaryExecutionLog logEntry = CanaryExecutionLog.builder()
                    .traceId(traceId)
                    .targetType(targetType)
                    .targetKey(targetKey)
                    .versionUsed(versionUsed)
                    .isCanary(isCanary)
                    .requestFeatures(featuresJson)
                    .executionTimeMs(executionTimeMs)
                    .errorMessage(errorMessage)
                    .build();

            canaryExecutionLogRepository.save(logEntry);

            log.debug("灰度执行错误日志已记录: traceId={}, targetType={}, targetKey={}",
                    traceId, targetType, targetKey);
        } catch (Exception e) {
            log.error("记录灰度执行错误日志失败: traceId={}, targetType={}, targetKey={}",
                    traceId, targetType, targetKey, e);
        }
    }

    /**
     * 查询灰度执行日志
     *
     * @param targetType 目标类型
     * @param targetKey  目标 Key
     * @param startTime  开始时间（可选）
     * @param endTime    结束时间（可选）
     * @return 日志列表
     */
    public List<CanaryExecutionLog> queryLogs(String targetType, String targetKey,
                                               LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null) {
            return canaryExecutionLogRepository
                    .findByTargetTypeAndTargetKeyAndCreatedAtBetween(
                            targetType, targetKey, startTime, endTime);
        }
        return canaryExecutionLogRepository
                .findByTargetTypeAndTargetKeyOrderByCreatedAtDesc(targetType, targetKey);
    }

    /**
     * 根据 traceId 查询日志
     *
     * @param traceId 追踪ID
     * @return 日志列表
     */
    public List<CanaryExecutionLog> queryByTraceId(String traceId) {
        return canaryExecutionLogRepository.findByTraceId(traceId);
    }
}
