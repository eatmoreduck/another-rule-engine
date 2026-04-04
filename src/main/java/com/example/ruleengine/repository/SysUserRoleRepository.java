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
interface SysUserRoleRepository extends JpaRepository<SysUserRoleRepository.SysUserRole, Long> {

    List<SysUserRole> findByUserId(Long userId);

    List<SysUserRole> findByRoleId(Long roleId);

    @Entity(name = "SysUserRole")
    @Table(name = "sys_user_role")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class SysUserRole {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "user_id", nullable = false)
        private Long userId;

        @Column(name = "role_id", nullable = false)
        private Long roleId;

        @Column(name = "created_at", nullable = false, updatable = false)
        private LocalDateTime createdAt;
    }
}
