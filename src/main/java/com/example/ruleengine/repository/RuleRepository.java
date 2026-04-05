package com.example.ruleengine.repository;

import com.example.ruleengine.domain.Rule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 规则数据访问接口
 * 优化：添加批量查询、EntityGraph 和投影查询，避免 N+1 问题
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
     * 检查 ruleKey 是否存在
     */
    boolean existsByRuleKey(String ruleKey);

    /**
     * 查询所有启用的规则
     */
    List<Rule> findByEnabledTrue();

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

    /**
     * 根据创建人分页查询
     */
    Page<Rule> findByCreatedBy(String createdBy, Pageable pageable);

    /**
     * 根据启用状态分页查询
     */
    Page<Rule> findByEnabled(Boolean enabled, Pageable pageable);

    /**
     * 关键词搜索（rule_key 或 rule_name 包含关键词）
     */
    @Query("SELECT r FROM Rule r WHERE r.ruleKey LIKE CONCAT('%', :keyword, '%') OR r.ruleName LIKE CONCAT('%', :keyword, '%')")
    Page<Rule> findByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 综合查询（支持多条件组合）
     */
    @Query("SELECT r FROM Rule r WHERE " +
           "(:createdBy IS NULL OR r.createdBy = :createdBy) AND " +
           "(:enabled IS NULL OR r.enabled = :enabled) AND " +
           "(:keyword IS NULL OR r.ruleKey LIKE CONCAT('%', :keyword, '%') OR r.ruleName LIKE CONCAT('%', :keyword, '%')) AND " +
           "(:deleted IS NULL OR r.deleted = :deleted) AND " +
           "(:createdAtStart IS NULL OR r.createdAt >= :createdAtStart) AND " +
           "(:createdAtEnd IS NULL OR r.createdAt <= :createdAtEnd) AND " +
           "(:updatedAtStart IS NULL OR r.updatedAt >= :updatedAtStart) AND " +
           "(:updatedAtEnd IS NULL OR r.updatedAt <= :updatedAtEnd)")
    Page<Rule> findByConditions(
            @Param("createdBy") String createdBy,
            @Param("enabled") Boolean enabled,
            @Param("keyword") String keyword,
            @Param("deleted") Boolean deleted,
            @Param("createdAtStart") LocalDateTime createdAtStart,
            @Param("createdAtEnd") LocalDateTime createdAtEnd,
            @Param("updatedAtStart") LocalDateTime updatedAtStart,
            @Param("updatedAtEnd") LocalDateTime updatedAtEnd,
            Pageable pageable
    );

    @Query("SELECT r FROM Rule r WHERE " +
           "(:createdBy IS NULL OR r.createdBy = :createdBy) AND " +
           "(:enabled IS NULL OR r.enabled = :enabled) AND " +
           "(:keyword IS NULL OR r.ruleKey LIKE CONCAT('%', :keyword, '%') OR r.ruleName LIKE CONCAT('%', :keyword, '%')) AND " +
           "(:deleted IS NULL OR r.deleted = :deleted)")
    Page<Rule> findByConditionsWithoutDates(
            @Param("createdBy") String createdBy,
            @Param("enabled") Boolean enabled,
            @Param("keyword") String keyword,
            @Param("deleted") Boolean deleted,
            Pageable pageable
    );

    /**
     * 根据环境ID查询规则
     */
    List<Rule> findByEnvironmentId(Long environmentId);

    /**
     * 根据环境ID和启用状态查询规则
     */
    List<Rule> findByEnvironmentIdAndEnabledTrue(Long environmentId);

    /**
     * 根据环境ID查询规则Key列表
     */
    @Query("SELECT r.ruleKey FROM Rule r WHERE r.environmentId = :environmentId")
    List<String> findRuleKeysByEnvironmentId(@Param("environmentId") Long environmentId);

    /**
     * 查询所有未删除的规则
     */
    List<Rule> findByDeletedFalse();

    /**
     * 分页查询未删除的规则
     */
    Page<Rule> findByDeletedFalse(Pageable pageable);

    /**
     * 根据 ruleKey 查询未删除的规则
     */
    Optional<Rule> findByRuleKeyAndDeletedFalse(String ruleKey);

    /**
     * 检查 ruleKey 对应的未删除规则是否存在
     */
    boolean existsByRuleKeyAndDeletedFalse(String ruleKey);

    /**
     * 综合查询 + 团队过滤（支持多条件组合）
     * 超级管理员不传 teamIds 或传空列表，普通用户传入其所属团队ID列表
     * team_id 为 NULL 的资源所有人可见
     */
    @Query("SELECT r FROM Rule r WHERE " +
           "(:createdBy IS NULL OR r.createdBy = :createdBy) AND " +
           "(:enabled IS NULL OR r.enabled = :enabled) AND " +
           "(:keyword IS NULL OR r.ruleKey LIKE CONCAT('%', :keyword, '%') OR r.ruleName LIKE CONCAT('%', :keyword, '%')) AND " +
           "(:deleted IS NULL OR r.deleted = :deleted) AND " +
           "(:createdAtStart IS NULL OR r.createdAt >= :createdAtStart) AND " +
           "(:createdAtEnd IS NULL OR r.createdAt <= :createdAtEnd) AND " +
           "(:updatedAtStart IS NULL OR r.updatedAt >= :updatedAtStart) AND " +
           "(:updatedAtEnd IS NULL OR r.updatedAt <= :updatedAtEnd) AND " +
           "(:teamFilter = false OR r.teamId IS NULL OR r.teamId IN :teamIds)")
    Page<Rule> findByConditionsWithTeam(
            @Param("createdBy") String createdBy,
            @Param("enabled") Boolean enabled,
            @Param("keyword") String keyword,
            @Param("deleted") Boolean deleted,
            @Param("createdAtStart") LocalDateTime createdAtStart,
            @Param("createdAtEnd") LocalDateTime createdAtEnd,
            @Param("updatedAtStart") LocalDateTime updatedAtStart,
            @Param("updatedAtEnd") LocalDateTime updatedAtEnd,
            @Param("teamFilter") boolean teamFilter,
            @Param("teamIds") List<Long> teamIds,
            Pageable pageable
    );

    @Query("SELECT r FROM Rule r WHERE " +
           "(:createdBy IS NULL OR r.createdBy = :createdBy) AND " +
           "(:enabled IS NULL OR r.enabled = :enabled) AND " +
           "(:keyword IS NULL OR r.ruleKey LIKE CONCAT('%', :keyword, '%') OR r.ruleName LIKE CONCAT('%', :keyword, '%')) AND " +
           "(:deleted IS NULL OR r.deleted = :deleted) AND " +
           "(:teamFilter = false OR r.teamId IS NULL OR r.teamId IN :teamIds)")
    Page<Rule> findByConditionsWithoutDatesAndWithTeam(
            @Param("createdBy") String createdBy,
            @Param("enabled") Boolean enabled,
            @Param("keyword") String keyword,
            @Param("deleted") Boolean deleted,
            @Param("teamFilter") boolean teamFilter,
            @Param("teamIds") List<Long> teamIds,
            Pageable pageable
    );

    /**
     * 分页查询未删除的规则（带团队过滤）
     */
    @Query("SELECT r FROM Rule r WHERE r.deleted = false AND " +
           "(:teamFilter = false OR r.teamId IS NULL OR r.teamId IN :teamIds)")
    Page<Rule> findByDeletedFalseWithTeam(
            @Param("teamFilter") boolean teamFilter,
            @Param("teamIds") List<Long> teamIds,
            Pageable pageable
    );
}
