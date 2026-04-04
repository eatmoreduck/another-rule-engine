package com.example.ruleengine.repository;

import com.example.ruleengine.domain.SysRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SysRolePermissionRepository extends JpaRepository<SysRolePermission, Long> {

    List<SysRolePermission> findByRoleId(Long roleId);
}
