package com.example.ruleengine.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 决策流版本实体类
 * 对应数据库表 decision_flow_versions
 */
@Entity
@Table(name = "decision_flow_versions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionFlowVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flow_id", nullable = false)
    private Long flowId;

    @Column(name = "flow_key", nullable = false, length = 255)
    private String flowKey;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "flow_graph", nullable = false, columnDefinition = "TEXT")
    private String flowGraph;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flow_id", insertable = false, updatable = false)
    private DecisionFlow decisionFlow;
}
