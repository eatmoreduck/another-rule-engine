package com.example.ruleengine.repository;

import com.example.ruleengine.constants.DecisionFlowStatus;
import com.example.ruleengine.domain.DecisionFlow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 决策流数据访问接口
 */
@Repository
public interface DecisionFlowRepository extends JpaRepository<DecisionFlow, Long> {

    /**
     * 根据 flowKey 查询决策流
     */
    Optional<DecisionFlow> findByFlowKey(String flowKey);

    /**
     * 根据 flowKey 查询启用的决策流
     */
    Optional<DecisionFlow> findByFlowKeyAndEnabledTrue(String flowKey);

    /**
     * 检查 flowKey 是否存在
     */
    boolean existsByFlowKey(String flowKey);

    /**
     * 根据状态查询决策流
     */
    List<DecisionFlow> findByStatus(DecisionFlowStatus status);

    /**
     * 查询所有启用的决策流
     */
    List<DecisionFlow> findByEnabledTrue();

    /**
     * 根据 flowKey 列表批量查询决策流
     */
    @Query("SELECT df FROM DecisionFlow df WHERE df.flowKey IN :flowKeys")
    List<DecisionFlow> findByFlowKeyIn(@Param("flowKeys") List<String> flowKeys);

    /**
     * 根据状态分页查询
     */
    Page<DecisionFlow> findByStatus(DecisionFlowStatus status, Pageable pageable);

    /**
     * 综合查询（支持多条件组合）
     */
    @Query("SELECT df FROM DecisionFlow df WHERE " +
           "(:status IS NULL OR df.status = :status) AND " +
           "(:createdBy IS NULL OR df.createdBy = :createdBy) AND " +
           "(:enabled IS NULL OR df.enabled = :enabled) AND " +
           "(:keyword IS NULL OR df.flowKey LIKE %:keyword% OR df.flowName LIKE %:keyword%) AND " +
           "(:createdAtStart IS NULL OR df.createdAt >= :createdAtStart) AND " +
           "(:createdAtEnd IS NULL OR df.createdAt <= :createdAtEnd) AND " +
           "(:updatedAtStart IS NULL OR df.updatedAt >= :updatedAtStart) AND " +
           "(:updatedAtEnd IS NULL OR df.updatedAt <= :updatedAtEnd)")
    Page<DecisionFlow> findByConditions(
            @Param("status") DecisionFlowStatus status,
            @Param("createdBy") String createdBy,
            @Param("enabled") Boolean enabled,
            @Param("keyword") String keyword,
            @Param("createdAtStart") LocalDateTime createdAtStart,
            @Param("createdAtEnd") LocalDateTime createdAtEnd,
            @Param("updatedAtStart") LocalDateTime updatedAtStart,
            @Param("updatedAtEnd") LocalDateTime updatedAtEnd,
            Pageable pageable
    );
}
