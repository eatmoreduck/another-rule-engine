package com.example.ruleengine.repository;

import com.example.ruleengine.constants.RuleStatus;
import com.example.ruleengine.domain.Rule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 规则数据访问接口
 */
@Repository
public interface RuleRepository extends JpaRepository<Rule, Long> {

    /**
     * 根据 ruleKey 查询规则
     */
    Optional<Rule> findByRuleKey(String ruleKey);

    /**
     * 根据 ruleKey 查询启用的规则
     */
    Optional<Rule> findByRuleKeyAndEnabledTrue(String ruleKey);

    /**
     * 根据 ruleKey 查询生效中的规则
     */
    Optional<Rule> findByRuleKeyAndStatusAndEnabledTrue(String ruleKey, RuleStatus status);

    /**
     * 检查 ruleKey 是否存在
     */
    boolean existsByRuleKey(String ruleKey);

    /**
     * 根据状态查询规则
     */
    List<Rule> findByStatus(RuleStatus status);

    /**
     * 查询所有启用的规则
     */
    List<Rule> findByEnabledTrue();

    /**
     * 根据状态查询启用的规则
     */
    List<Rule> findByStatusAndEnabledTrue(RuleStatus status);

    /**
     * 根据 ruleKey 列表批量查询规则
     */
    @Query("SELECT r FROM Rule r WHERE r.ruleKey IN :ruleKeys")
    List<Rule> findByRuleKeyIn(@Param("ruleKeys") List<String> ruleKeys);

    /**
     * 只查询规则脚本（投影查询，避免加载整个实体）
     */
    @Query("SELECT r.groovyScript FROM Rule r WHERE r.ruleKey = :ruleKey AND r.enabled = true")
    Optional<String> findScriptByKey(@Param("ruleKey") String ruleKey);
}
