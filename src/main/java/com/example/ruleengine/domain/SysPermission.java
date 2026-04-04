package com.example.ruleengine.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统权限实体
 */
@Entity
@Table(name = "sys_permission")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "permission_code", unique = true, nullable = false, length = 200)
    private String permissionCode;

    @Column(name = "permission_name", nullable = false, length = 200)
    private String permissionName;

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;

    @Column(name = "resource_path", length = 500)
    private String resourcePath;

    @Column(name = "method", length = 10)
    private String method;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
