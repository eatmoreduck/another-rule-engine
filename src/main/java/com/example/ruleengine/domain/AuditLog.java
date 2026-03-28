package com.example.ruleengine.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 审计日志实体类
 */
@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 255)
    private String entityId;

    @Column(name = "operation", nullable = false, length = 50)
    private String operation;

    @Column(name = "operation_detail", columnDefinition = "TEXT")
    private String operationDetail;

    @Column(name = "operator", nullable = false, length = 255)
    private String operator;

    @Column(name = "operator_ip", length = 50)
    private String operatorIp;

    @CreationTimestamp
    @Column(name = "operation_time", nullable = false, updatable = false)
    private LocalDateTime operationTime;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "SUCCESS";

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "request_id", length = 100)
    private String requestId;
}
