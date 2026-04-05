package com.example.ruleengine.repository;

import com.example.ruleengine.domain.SysTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 团队数据访问接口
 */
@Repository
public interface SysTeamRepository extends JpaRepository<SysTeam, Long> {

    Optional<SysTeam> findByTeamCode(String teamCode);

    boolean existsByTeamCode(String teamCode);
}
