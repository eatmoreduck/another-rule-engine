package com.example.ruleengine.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 灰度指标实体类
 * 对应数据库表 grayscale_metrics
 */
@Entity
@Table(name = "grayscale_metrics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrayscaleMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grayscale_config_id", nullable = false)
    private Long grayscaleConfigId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "execution_count", nullable = false)
    @Builder.Default
    private Integer executionCount = 0;

    @Column(name = "hit_count", nullable = false)
    @Builder.Default
    private Integer hitCount = 0;

    @Column(name = "error_count", nullable = false)
    @Builder.Default
    private Integer errorCount = 0;

    @Column(name = "avg_execution_time_ms", nullable = false)
    @Builder.Default
    private Integer avgExecutionTimeMs = 0;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;
}
