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
 * 系统预置规则模板实体类
 * 对应数据库表 rule_templates
 */
@Entity
@Table(name = "rule_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "groovy_template", nullable = false, columnDefinition = "TEXT")
    private String groovyTemplate;

    @Column(name = "parameters", columnDefinition = "jsonb")
    private String parameters;

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private Boolean isSystem = false;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
