package com.example.ruleengine.domain;

import com.example.ruleengine.constants.GrayscaleStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 灰度发布配置实体类
 * 对应数据库表 grayscale_configs
 */
@Entity
@Table(name = "grayscale_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrayscaleConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_key", nullable = false, length = 255)
    private String ruleKey;

    @Column(name = "target_type", length = 20)
    @Builder.Default
    private String targetType = "RULE";

    @Column(name = "target_key", length = 255)
    private String targetKey;

    @Column(name = "current_version", nullable = false)
    private Integer currentVersion;

    @Column(name = "grayscale_version", nullable = false)
    private Integer grayscaleVersion;

    @Column(name = "grayscale_percentage", nullable = false)
    @Builder.Default
    private Integer grayscalePercentage = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private GrayscaleStatus status = GrayscaleStatus.DRAFT;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
