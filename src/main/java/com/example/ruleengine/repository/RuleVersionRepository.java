package com.example.ruleengine.repository;

import com.example.ruleengine.domain.RuleVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 规则版本历史数据访问接口
 */
@Repository
public interface RuleVersionRepository extends JpaRepository<RuleVersion, Long> {

    /**
     * 根据规则ID查询所有版本（按版本号降序）
     */
    List<RuleVersion> findByRuleIdOrderByVersionDesc(Long ruleId);

    /**
     * 根据规则ID和版本号查询
     */
    Optional<RuleVersion> findByRuleIdAndVersion(Long ruleId, Integer version);

    /**
     * 根据规则ID查询最新版本
     */
    Optional<RuleVersion> findFirstByRuleIdOrderByVersionDesc(Long ruleId);

    /**
     * 根据 ruleKey 查询所有版本（按版本号降序）
     */
    List<RuleVersion> findByRuleKeyOrderByVersionDesc(String ruleKey);

    /**
     * 根据 ruleKey 和版本号查询
     */
    @Query("SELECT rv FROM RuleVersion rv WHERE rv.ruleKey = :ruleKey AND rv.version = :version")
    Optional<RuleVersion> findByRuleKeyAndVersion(@Param("ruleKey") String ruleKey, @Param("version") Integer version);

    /**
     * 查询规则的所有版本号
     */
    @Query("SELECT rv.version FROM RuleVersion rv WHERE rv.ruleId = :ruleId ORDER BY rv.version DESC")
    List<Integer> findVersionsByRuleId(@Param("ruleId") Long ruleId);

    /**
     * 查询是否为回滚版本
     */
    @Query("SELECT rv FROM RuleVersion rv WHERE rv.ruleId = :ruleId AND rv.isRollback = true")
    List<RuleVersion> findRollbackVersionsByRuleId(@Param("ruleId") Long ruleId);
}
