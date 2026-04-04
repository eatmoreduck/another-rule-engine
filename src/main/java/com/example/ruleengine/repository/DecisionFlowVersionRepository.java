package com.example.ruleengine.repository;

import com.example.ruleengine.constants.VersionStatus;
import com.example.ruleengine.domain.DecisionFlowVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 决策流版本数据访问接口
 */
@Repository
public interface DecisionFlowVersionRepository extends JpaRepository<DecisionFlowVersion, Long> {

    /**
     * 根据 flowKey 查询所有版本（按版本号倒序）
     */
    List<DecisionFlowVersion> findByFlowKeyOrderByVersionDesc(String flowKey);

    /**
     * 根据 flowKey 和版本号查询指定版本
     */
    Optional<DecisionFlowVersion> findByFlowKeyAndVersion(String flowKey, Integer version);

    /**
     * 根据 flowKey 查询最新版本
     */
    Optional<DecisionFlowVersion> findTopByFlowKeyOrderByVersionDesc(String flowKey);

    /**
     * 根据 flowId 查询所有版本（按版本号倒序）
     */
    List<DecisionFlowVersion> findByFlowIdOrderByVersionDesc(Long flowId);

    /**
     * 根据决策流Key和版本状态查询版本列表
     */
    List<DecisionFlowVersion> findByFlowKeyAndStatus(String flowKey, VersionStatus status);

    /**
     * 根据决策流Key和版本状态查询最新版本（按版本号降序取第一条）
     */
    Optional<DecisionFlowVersion> findTopByFlowKeyAndStatusOrderByVersionDesc(String flowKey, VersionStatus status);
}
