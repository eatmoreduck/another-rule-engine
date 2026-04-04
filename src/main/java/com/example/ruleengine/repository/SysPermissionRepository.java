package com.example.ruleengine.repository;

import com.example.ruleengine.domain.SysPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SysPermissionRepository extends JpaRepository<SysPermission, Long> {

    @Query("SELECT p FROM SysPermission p " +
           "JOIN SysRolePermission rp ON p.id = rp.permissionId " +
           "JOIN SysUserRole ur ON rp.roleId = ur.roleId " +
           "WHERE ur.userId = :userId AND p.status = 'ACTIVE'")
    List<SysPermission> findPermissionsByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT r.roleCode FROM SysRole r " +
           "JOIN SysUserRole ur ON r.id = ur.roleId " +
           "WHERE ur.userId = :userId AND r.status = 'ACTIVE'")
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);
}
