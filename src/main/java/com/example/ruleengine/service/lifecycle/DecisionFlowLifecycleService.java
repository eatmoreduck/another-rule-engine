package com.example.ruleengine.service.lifecycle;

import com.example.ruleengine.constants.DecisionFlowStatus;
import com.example.ruleengine.constants.VersionStatus;
import com.example.ruleengine.domain.DecisionFlow;
import com.example.ruleengine.domain.DecisionFlowVersion;
import com.example.ruleengine.model.dto.CreateDecisionFlowRequest;
import com.example.ruleengine.model.dto.DecisionFlowQuery;
import com.example.ruleengine.model.dto.UpdateDecisionFlowRequest;
import com.example.ruleengine.repository.DecisionFlowRepository;
import com.example.ruleengine.repository.DecisionFlowVersionRepository;
import com.example.ruleengine.service.auth.DataPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 决策流生命周期管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DecisionFlowLifecycleService {

    private final DecisionFlowRepository decisionFlowRepository;
    private final DecisionFlowVersionRepository decisionFlowVersionRepository;
    private final DataPermissionService dataPermissionService;

    /**
     * 创建决策流
     */
    @Transactional
    public DecisionFlow createFlow(CreateDecisionFlowRequest request, String operator) {
        if (decisionFlowRepository.existsByFlowKey(request.getFlowKey())) {
            throw new IllegalArgumentException("决策流Key已存在: " + request.getFlowKey());
        }

        DecisionFlow flow = DecisionFlow.builder()
                .flowKey(request.getFlowKey())
                .flowName(request.getFlowName())
                .flowDescription(request.getFlowDescription())
                .flowGraph(request.getFlowGraph())
                .version(1)
                .status(DecisionFlowStatus.DRAFT)
                .createdBy(operator)
                .enabled(true)
                .build();

        DecisionFlow saved = decisionFlowRepository.save(flow);

        // 保存初始版本记录
        DecisionFlowVersion initialVersion = DecisionFlowVersion.builder()
                .flowId(saved.getId())
                .flowKey(saved.getFlowKey())
                .version(1)
                .flowGraph(saved.getFlowGraph())
                .changeReason("创建决策流")
                .changedBy(operator)
                .isRollback(false)
                .status(VersionStatus.ACTIVE)
                .build();
        decisionFlowVersionRepository.save(initialVersion);

        log.info("创建决策流: flowKey={}, operator={}", request.getFlowKey(), operator);
        return saved;
    }

    /**
     * 更新决策流
     */
    @Transactional
    public DecisionFlow updateFlow(String flowKey, UpdateDecisionFlowRequest request, String operator) {
        DecisionFlow flow = decisionFlowRepository.findByFlowKey(flowKey)
                .orElseThrow(() -> new IllegalArgumentException("决策流不存在: " + flowKey));

        if (request.getFlowName() != null) {
            flow.setFlowName(request.getFlowName());
        }
        if (request.getFlowDescription() != null) {
            flow.setFlowDescription(request.getFlowDescription());
        }
        if (request.getFlowGraph() != null && !request.getFlowGraph().equals(flow.getFlowGraph())) {
            flow.setFlowGraph(request.getFlowGraph());
            flow.setVersion(flow.getVersion() + 1);

            // 将当前 ACTIVE 版本归档
            decisionFlowVersionRepository.findByFlowKeyAndStatus(flowKey, VersionStatus.ACTIVE)
                    .forEach(v -> {
                        v.setStatus(VersionStatus.ARCHIVED);
                        decisionFlowVersionRepository.save(v);
                    });

            // 保存新版本记录
            DecisionFlowVersion newVersion = DecisionFlowVersion.builder()
                    .flowId(flow.getId())
                    .flowKey(flowKey)
                    .version(flow.getVersion())
                    .flowGraph(request.getFlowGraph())
                    .changeReason(request.getChangeReason())
                    .changedBy(operator)
                    .isRollback(false)
                    .status(VersionStatus.ACTIVE)
                    .build();
            decisionFlowVersionRepository.save(newVersion);
        }
        flow.setUpdatedBy(operator);

        DecisionFlow updated = decisionFlowRepository.save(flow);
        log.info("更新决策流: flowKey={}, operator={}", flowKey, operator);
        return updated;
    }

    /**
     * 删除决策流（软删除）
     */
    @Transactional
    public void deleteFlow(String flowKey, String operator) {
        DecisionFlow flow = decisionFlowRepository.findByFlowKey(flowKey)
                .orElseThrow(() -> new IllegalArgumentException("决策流不存在: " + flowKey));

        flow.setStatus(DecisionFlowStatus.DELETED);
        flow.setEnabled(false);
        flow.setUpdatedBy(operator);
        decisionFlowRepository.save(flow);
        log.info("删除决策流: flowKey={}, operator={}", flowKey, operator);
    }

    /**
     * 启用决策流
     */
    @Transactional
    public DecisionFlow enableFlow(String flowKey, String operator) {
        DecisionFlow flow = decisionFlowRepository.findByFlowKey(flowKey)
                .orElseThrow(() -> new IllegalArgumentException("决策流不存在: " + flowKey));
        flow.setStatus(DecisionFlowStatus.ACTIVE);
        flow.setEnabled(true);
        flow.setUpdatedBy(operator);
        return decisionFlowRepository.save(flow);
    }

    /**
     * 禁用决策流
     */
    @Transactional
    public DecisionFlow disableFlow(String flowKey, String operator) {
        DecisionFlow flow = decisionFlowRepository.findByFlowKey(flowKey)
                .orElseThrow(() -> new IllegalArgumentException("决策流不存在: " + flowKey));
        flow.setEnabled(false);
        flow.setUpdatedBy(operator);
        return decisionFlowRepository.save(flow);
    }

    /**
     * 获取决策流详情
     */
    public DecisionFlow getFlow(String flowKey) {
        return decisionFlowRepository.findByFlowKey(flowKey)
                .orElseThrow(() -> new IllegalArgumentException("决策流不存在: " + flowKey));
    }

    /**
     * 列出所有决策流（分页）
     */
    public Page<DecisionFlow> listFlows(Pageable pageable) {
        boolean isAdmin = dataPermissionService.isCurrentUserAdmin();
        boolean teamFilter = !isAdmin;
        List<Long> teamIds = isAdmin ? List.of() : dataPermissionService.getCurrentUserTeamIds();
        return decisionFlowRepository.findAllWithTeam(teamFilter, teamIds, pageable);
    }

    /**
     * 查询决策流（支持多条件过滤）
     */
    public Page<DecisionFlow> queryFlows(DecisionFlowQuery query, Pageable pageable) {
        log.info("查询决策流: query={}", query);
        boolean isAdmin = dataPermissionService.isCurrentUserAdmin();
        boolean teamFilter = !isAdmin;
        List<Long> teamIds = isAdmin ? List.of() : dataPermissionService.getCurrentUserTeamIds();
        return decisionFlowRepository.findByConditionsWithTeam(
                query.getStatus(),
                query.getCreatedBy(),
                query.getEnabled(),
                query.getKeyword(),
                query.getCreatedAtStart(),
                query.getCreatedAtEnd(),
                query.getUpdatedAtStart(),
                query.getUpdatedAtEnd(),
                teamFilter,
                teamIds,
                pageable
        );
    }
}
