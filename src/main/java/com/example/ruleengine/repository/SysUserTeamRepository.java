package com.example.ruleengine.repository;

import com.example.ruleengine.domain.SysUserTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户-团队关联数据访问接口
 */
@Repository
public interface SysUserTeamRepository extends JpaRepository<SysUserTeam, Long> {

    List<SysUserTeam> findByUserId(Long userId);

    List<SysUserTeam> findByTeamId(Long teamId);

    void deleteByUserIdAndTeamId(Long userId, Long teamId);

    void deleteByTeamId(Long teamId);

    boolean existsByUserIdAndTeamId(Long userId, Long teamId);
}
