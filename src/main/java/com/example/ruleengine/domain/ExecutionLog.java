package com.example.ruleengine.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 规则执行日志实体类
 */
@Entity
@Table(name = "execution_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_key", nullable = false, length = 255)
    private String ruleKey;

    @Column(name = "rule_version")
    private Integer ruleVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_features", columnDefinition = "jsonb")
    private Map<String, Object> inputFeatures;

    @Column(name = "output_decision", length = 50)
    private String outputDecision;

    @Column(name = "output_reason", columnDefinition = "TEXT")
    private String outputReason;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "SUCCESS";

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
