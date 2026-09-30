package com.example.ruleengine.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 特征别名实体
 */
@Entity
@Table(name = "feature_alias")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alias_code", unique = true, nullable = false, length = 120)
    private String aliasCode;

    @Column(name = "canonical_code", nullable = false, length = 120)
    private String canonicalCode;

    @Column(name = "alias_type", nullable = false, length = 50)
    @Builder.Default
    private String aliasType = "LEGACY";

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
