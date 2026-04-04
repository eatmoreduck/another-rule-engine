package com.example.ruleengine.repository;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
interface SysRolePermissionRepository extends JpaRepository<SysRolePermissionRepository.SysRolePermission, Long> {

    List<SysRolePermission> findByRoleId(Long roleId);

    @Entity(name = "SysRolePermission")
    @Table(name = "sys_role_permission")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class SysRolePermission {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "role_id", nullable = false)
        private Long roleId;

        @Column(name = "permission_id", nullable = false)
        private Long permissionId;

        @Column(name = "created_at", nullable = false, updatable = false)
        private LocalDateTime createdAt;
    }
}
