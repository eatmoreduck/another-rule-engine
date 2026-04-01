package com.example.ruleengine.controller;

import com.example.ruleengine.domain.DecisionFlow;
import com.example.ruleengine.model.dto.CreateDecisionFlowRequest;
import com.example.ruleengine.model.dto.DecisionFlowQuery;
import com.example.ruleengine.model.dto.UpdateDecisionFlowRequest;
import com.example.ruleengine.service.lifecycle.DecisionFlowLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 决策流管理 API 控制器
 */
@RestController
@RequestMapping("/api/v1/decision-flows")
@RequiredArgsConstructor
@Slf4j
public class DecisionFlowController {

    private final DecisionFlowLifecycleService decisionFlowLifecycleService;

    /**
     * 创建决策流
     */
    @PostMapping
    public ResponseEntity<DecisionFlow> createFlow(
            @Valid @RequestBody CreateDecisionFlowRequest request,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        log.info("创建决策流: flowKey={}, operator={}", request.getFlowKey(), operator);
        DecisionFlow flow = decisionFlowLifecycleService.createFlow(request, operator);
        return ResponseEntity.ok(flow);
    }

    /**
     * 更新决策流
     */
    @PutMapping("/{flowKey}")
    public ResponseEntity<DecisionFlow> updateFlow(
            @PathVariable String flowKey,
            @Valid @RequestBody UpdateDecisionFlowRequest request,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        log.info("更新决策流: flowKey={}, operator={}", flowKey, operator);
        DecisionFlow flow = decisionFlowLifecycleService.updateFlow(flowKey, request, operator);
        return ResponseEntity.ok(flow);
    }

    /**
     * 删除决策流（软删除）
     */
    @DeleteMapping("/{flowKey}")
    public ResponseEntity<Void> deleteFlow(
            @PathVariable String flowKey,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        log.info("删除决策流: flowKey={}, operator={}", flowKey, operator);
        decisionFlowLifecycleService.deleteFlow(flowKey, operator);
        return ResponseEntity.ok().build();
    }

    /**
     * 启用决策流
     */
    @PostMapping("/{flowKey}/enable")
    public ResponseEntity<DecisionFlow> enableFlow(
            @PathVariable String flowKey,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        log.info("启用决策流: flowKey={}, operator={}", flowKey, operator);
        DecisionFlow flow = decisionFlowLifecycleService.enableFlow(flowKey, operator);
        return ResponseEntity.ok(flow);
    }

    /**
     * 禁用决策流
     */
    @PostMapping("/{flowKey}/disable")
    public ResponseEntity<DecisionFlow> disableFlow(
            @PathVariable String flowKey,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        log.info("禁用决策流: flowKey={}, operator={}", flowKey, operator);
        DecisionFlow flow = decisionFlowLifecycleService.disableFlow(flowKey, operator);
        return ResponseEntity.ok(flow);
    }

    /**
     * 获取决策流详情
     */
    @GetMapping("/{flowKey}")
    public ResponseEntity<DecisionFlow> getFlow(@PathVariable String flowKey) {
        DecisionFlow flow = decisionFlowLifecycleService.getFlow(flowKey);
        return ResponseEntity.ok(flow);
    }

    /**
     * 列出所有决策流（分页）
     */
    @GetMapping
    public ResponseEntity<Page<DecisionFlow>> listFlows(Pageable pageable) {
        Page<DecisionFlow> flows = decisionFlowLifecycleService.listFlows(pageable);
        return ResponseEntity.ok(flows);
    }

    /**
     * 查询决策流（支持多条件过滤）
     */
    @PostMapping("/query")
    public ResponseEntity<Page<DecisionFlow>> queryFlows(
            @RequestBody DecisionFlowQuery query,
            Pageable pageable) {
        log.info("查询决策流: query={}", query);
        Page<DecisionFlow> flows = decisionFlowLifecycleService.queryFlows(query, pageable);
        return ResponseEntity.ok(flows);
    }
}
