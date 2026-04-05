package com.example.ruleengine.service.version;

import com.example.ruleengine.cache.DecisionFlowCacheService;
import com.example.ruleengine.cache.RuleCacheService;
import com.example.ruleengine.constants.VersionStatus;
import com.example.ruleengine.domain.DecisionFlow;
import com.example.ruleengine.domain.DecisionFlowVersion;
import com.example.ruleengine.repository.DecisionFlowRepository;
import com.example.ruleengine.repository.DecisionFlowVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 决策流版本管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DecisionFlowVersionManagementService {

    private final DecisionFlowVersionRepository versionRepository;
    private final DecisionFlowRepository flowRepository;
    private final DecisionFlowCacheService decisionFlowCacheService;
    private final RuleCacheService ruleCacheService;

    /**
     * 查询决策流的所有版本
     */
    public List<DecisionFlowVersion> listVersions(String flowKey) {
        return versionRepository.findByFlowKeyOrderByVersionDesc(flowKey);
    }

    /**
     * 查询决策流的指定版本
     */
    public Optional<DecisionFlowVersion> getVersion(String flowKey, Integer version) {
        return versionRepository.findByFlowKeyAndVersion(flowKey, version);
    }

    /**
     * 查询决策流的最新版本
     */
    public Optional<DecisionFlowVersion> getLatestVersion(String flowKey) {
        return versionRepository.findTopByFlowKeyOrderByVersionDesc(flowKey);
    }

    /**
     * 回滚决策流到指定版本
     */
    @Transactional
    public DecisionFlowVersion rollback(String flowKey, Integer targetVersion, String operator) {
        DecisionFlow flow = flowRepository.findByFlowKey(flowKey)
                .orElseThrow(() -> new IllegalArgumentException("决策流不存在: " + flowKey));

        DecisionFlowVersion targetVer = versionRepository.findByFlowKeyAndVersion(flowKey, targetVersion)
                .orElseThrow(() -> new IllegalArgumentException("版本不存在: " + targetVersion));

        int newVersion = flow.getVersion() + 1;

        DecisionFlowVersion rollbackVersion = DecisionFlowVersion.builder()
                .flowId(flow.getId())
                .flowKey(flowKey)
                .version(newVersion)
                .flowGraph(targetVer.getFlowGraph())
                .changeReason("回滚到版本 " + targetVersion)
                .changedBy(operator)
                .isRollback(true)
                .rollbackFromVersion(flow.getVersion())
                .build();

        versionRepository.save(rollbackVersion);

        flow.setFlowGraph(targetVer.getFlowGraph());
        flow.setVersion(newVersion);
        flow.setUpdatedBy(operator);
        flowRepository.save(flow);

        log.info("决策流版本回滚: flowKey={}, targetVersion={}, newVersion={}", flowKey, targetVersion, newVersion);
        return rollbackVersion;
    }

    /**
     * 创建决策流草稿版本
     * 新版本状态为 DRAFT，不影响当前生效版本
     *
     * @param flowKey      决策流Key
     * @param flowGraph    决策流图JSON
     * @param changeReason 变更原因
     * @param operator     操作人
     * @return 草稿版本
     */
    @Transactional
    public DecisionFlowVersion createDraft(String flowKey, String flowGraph, String changeReason, String operator) {
        DecisionFlow flow = flowRepository.findByFlowKey(flowKey)
                .orElseThrow(() -> new IllegalArgumentException("决策流不存在: " + flowKey));

        int newVersion = flow.getVersion() + 1;

        DecisionFlowVersion draftVersion = DecisionFlowVersion.builder()
                .flowId(flow.getId())
                .flowKey(flowKey)
                .version(newVersion)
                .flowGraph(flowGraph)
                .changeReason(changeReason)
                .changedBy(operator)
                .isRollback(false)
                .status(VersionStatus.DRAFT)
                .build();

        draftVersion = versionRepository.save(draftVersion);

        log.info("创建决策流草稿版本: flowKey={}, version={}, operator={}", flowKey, newVersion, operator);
        return draftVersion;
    }

    /**
     * 发布决策流版本
     * 将指定版本设为 ACTIVE，旧 ACTIVE 版本设为 ARCHIVED，更新主表
     *
     * @param flowKey 决策流Key
     * @param version 要发布的版本号
     * @param operator 操作人
     * @return 发布后的版本
     */
    @Transactional
    public DecisionFlowVersion publishVersion(String flowKey, Integer version, String operator) {
        DecisionFlow flow = flowRepository.findByFlowKey(flowKey)
                .orElseThrow(() -> new IllegalArgumentException("决策流不存在: " + flowKey));

        DecisionFlowVersion targetVersion = versionRepository.findByFlowKeyAndVersion(flowKey, version)
                .orElseThrow(() -> new IllegalArgumentException("版本不存在: " + version));

        // 只有 DRAFT 或 CANARY 状态的版本才能发布
        if (targetVersion.getStatus() != VersionStatus.DRAFT
                && targetVersion.getStatus() != VersionStatus.CANARY) {
            throw new IllegalArgumentException("只有草稿或灰度中的版本才能发布，当前状态: " + targetVersion.getStatus());
        }

        // 将当前 ACTIVE 版本设为 ARCHIVED
        versionRepository.findByFlowKeyAndStatus(flowKey, VersionStatus.ACTIVE)
                .forEach(activeVersion -> {
                    activeVersion.setStatus(VersionStatus.ARCHIVED);
                    versionRepository.save(activeVersion);
                });

        // 将目标版本设为 ACTIVE
        targetVersion.setStatus(VersionStatus.ACTIVE);
        versionRepository.save(targetVersion);

        // 更新主表
        flow.setFlowGraph(targetVersion.getFlowGraph());
        flow.setVersion(targetVersion.getVersion());
        flow.setActiveVersion(targetVersion.getVersion());
        flow.setUpdatedBy(operator);
        flowRepository.save(flow);

        log.info("发布决策流版本: flowKey={}, version={}, operator={}", flowKey, version, operator);

        // 清除缓存：决策流主数据、版本缓存、灰度配置缓存
        decisionFlowCacheService.evictFlow(flowKey);
        decisionFlowCacheService.evictFlowVersion(flowKey, version);
        ruleCacheService.evictGrayscaleConfig("DECISION_FLOW", flowKey);

        return targetVersion;
    }
}
