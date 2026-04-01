package com.example.ruleengine.service.lifecycle;

import com.example.ruleengine.constants.DecisionFlowStatus;
import com.example.ruleengine.domain.DecisionFlow;
import com.example.ruleengine.model.dto.CreateDecisionFlowRequest;
import com.example.ruleengine.model.dto.DecisionFlowQuery;
import com.example.ruleengine.model.dto.UpdateDecisionFlowRequest;
import com.example.ruleengine.repository.DecisionFlowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 决策流生命周期管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DecisionFlowLifecycleService {

    private final DecisionFlowRepository decisionFlowRepository;

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
        return decisionFlowRepository.findAll(pageable);
    }

    /**
     * 查询决策流（支持多条件过滤）
     */
    public Page<DecisionFlow> queryFlows(DecisionFlowQuery query, Pageable pageable) {
        log.info("查询决策流: query={}", query);
        return decisionFlowRepository.findByConditions(
                query.getStatus(),
                query.getCreatedBy(),
                query.getEnabled(),
                query.getKeyword(),
                query.getCreatedAtStart(),
                query.getCreatedAtEnd(),
                query.getUpdatedAtStart(),
                query.getUpdatedAtEnd(),
                pageable
        );
    }
}
