package com.example.ruleengine.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 特征定义实体
 */
@Entity
@Table(name = "feature_definition")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", unique = true, nullable = false, length = 120)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "data_type", nullable = false, length = 50)
    private String dataType;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "example_value", columnDefinition = "TEXT")
    private String exampleValue;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "scope", length = 100)
    private String scope;

    @Column(name = "sensitivity", nullable = false, length = 50)
    @Builder.Default
    private String sensitivity = "NORMAL";

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "owner", length = 100)
    private String owner;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
