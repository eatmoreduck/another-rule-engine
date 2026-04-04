package com.example.ruleengine.repository;

import com.example.ruleengine.domain.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysRoleRepository extends JpaRepository<SysRole, Long> {

    Optional<SysRole> findByRoleCode(String roleCode);

    List<SysRole> findByIdIn(List<Long> ids);
}
