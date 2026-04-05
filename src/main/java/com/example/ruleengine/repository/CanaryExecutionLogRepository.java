package com.example.ruleengine.repository;

import com.example.ruleengine.domain.CanaryExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 灰度执行日志数据访问接口
 */
@Repository
public interface CanaryExecutionLogRepository extends JpaRepository<CanaryExecutionLog, Long> {

    /**
     * 根据 traceId 查询日志
     */
    List<CanaryExecutionLog> findByTraceId(String traceId);

    /**
     * 根据目标类型和目标Key查询日志（按时间降序）
     */
    List<CanaryExecutionLog> findByTargetTypeAndTargetKeyOrderByCreatedAtDesc(
            String targetType, String targetKey);

    /**
     * 根据目标类型、目标Key和时间范围查询日志
     */
    List<CanaryExecutionLog> findByTargetTypeAndTargetKeyAndCreatedAtBetween(
            String targetType, String targetKey,
            LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 根据目标类型和目标Key查询灰度命中的日志（按时间降序）
     */
    List<CanaryExecutionLog> findByTargetTypeAndTargetKeyAndIsCanaryTrueOrderByCreatedAtDesc(
            String targetType, String targetKey);

    /**
     * 根据目标类型、目标Key、是否灰度和时间范围查询日志
     */
    List<CanaryExecutionLog> findByTargetTypeAndTargetKeyAndIsCanaryAndCreatedAtBetween(
            String targetType, String targetKey, Boolean isCanary,
            LocalDateTime startTime, LocalDateTime endTime);
}
