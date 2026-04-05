package com.example.ruleengine.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 灰度执行日志实体
 * 对应数据库表 canary_execution_log
 * 记录灰度分流的每次执行详情
 */
@Entity
@Table(name = "canary_execution_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CanaryExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 追踪ID（关联请求链路）
     */
    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    /**
     * 目标类型：RULE / DECISION_FLOW
     */
    @Column(name = "target_type", nullable = false, length = 20)
    @Builder.Default
    private String targetType = "RULE";

    /**
     * 目标 Key（规则 Key 或决策流 Key）
     */
    @Column(name = "target_key", nullable = false, length = 255)
    private String targetKey;

    /**
     * 使用的版本号
     */
    @Column(name = "version_used", nullable = false)
    private Integer versionUsed;

    /**
     * 是否命中灰度
     */
    @Column(name = "is_canary", nullable = false)
    @Builder.Default
    private Boolean isCanary = false;

    /**
     * 请求特征数据（JSONB）
     */
    @Column(name = "request_features", columnDefinition = "jsonb")
    private String requestFeatures;

    /**
     * 决策结果
     */
    @Column(name = "decision_result", length = 50)
    private String decisionResult;

    /**
     * 执行耗时（毫秒）
     */
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    /**
     * 错误信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
