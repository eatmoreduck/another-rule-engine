package com.example.ruleengine.domain;

import com.example.ruleengine.constants.RuleStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 规则实体类
 * 对应数据库表 rules
 */
@Entity
@Table(name = "rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_key", unique = true, nullable = false, length = 255)
    private String ruleKey;

    @Column(name = "rule_name", nullable = false, length = 255)
    private String ruleName;

    @Column(name = "rule_description", columnDefinition = "TEXT")
    private String ruleDescription;

    @Column(name = "groovy_script", nullable = false, columnDefinition = "TEXT")
    private String groovyScript;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private RuleStatus status = RuleStatus.DRAFT;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 乐观锁版本号
     */
    @Version
    private Long optLockVersion;

    /**
     * 所属环境ID
     */
    @Column(name = "environment_id")
    private Long environmentId;
}
