package com.example.ruleengine.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 规则版本历史实体类
 * 对应数据库表 rule_versions
 */
@Entity
@Table(name = "rule_versions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "rule_key", nullable = false, length = 255)
    private String ruleKey;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "groovy_script", nullable = false, columnDefinition = "TEXT")
    private String groovyScript;

    @Column(name = "change_reason", columnDefinition = "TEXT")
    private String changeReason;

    @Column(name = "changed_by", nullable = false, length = 255)
    private String changedBy;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @Column(name = "is_rollback")
    @Builder.Default
    private Boolean isRollback = false;

    @Column(name = "rollback_from_version")
    private Integer rollbackFromVersion;

    /**
     * 关联的规则实体（多对一）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", insertable = false, updatable = false)
    private Rule rule;
}
