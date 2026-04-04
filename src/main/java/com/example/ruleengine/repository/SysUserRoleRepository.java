package com.example.ruleengine.repository;

import com.example.ruleengine.domain.SysUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SysUserRoleRepository extends JpaRepository<SysUserRole, Long> {

    List<SysUserRole> findByUserId(Long userId);

    List<SysUserRole> findByRoleId(Long roleId);
}
