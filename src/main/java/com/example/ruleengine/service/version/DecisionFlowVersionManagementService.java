package com.example.ruleengine.service.version;

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
}
