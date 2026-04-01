package com.example.ruleengine.controller;

import com.example.ruleengine.model.DecisionRequest;
import com.example.ruleengine.model.DecisionResponse;
import com.example.ruleengine.service.DecisionFlowExecutionService;
import com.example.ruleengine.service.RuleExecutionService;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 决策 API 控制器
 * Source: CONTEXT.md 决策 D-16, D-17, D-18, D-19
 *
 * 功能：
 * 1. REXEC-01: 同步 API 接收决策请求，50ms 内返回结果
 * 2. POST /api/v1/decide 端点
 * 3. JSON 请求/响应格式
 */
@RestController
@RequestMapping("/api/v1")
@Timed(value = "decision.api", description = "Decision API performance")
public class DecisionController {

    private static final Logger logger = LoggerFactory.getLogger(DecisionController.class);

    private final RuleExecutionService ruleExecutionService;
    private final DecisionFlowExecutionService decisionFlowExecutionService;

    public DecisionController(RuleExecutionService ruleExecutionService,
                              DecisionFlowExecutionService decisionFlowExecutionService) {
        this.ruleExecutionService = ruleExecutionService;
        this.decisionFlowExecutionService = decisionFlowExecutionService;
    }

    /**
     * 决策 API
     * D-16: POST /api/v1/decide
     * D-17: 请求格式 {ruleId, features}
     * D-18: 响应格式 {decision, reason, executionTimeMs}
     * D-19: API 响应时间要求 < 50ms（P95）
     */
    @PostMapping(
        value = "/decide",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<DecisionResponse> decide(@Valid @RequestBody DecisionRequest request) {
        logger.info("Received decision request for rule: {}", request.getRuleId());

        try {
            DecisionResponse response = ruleExecutionService.executeDecision(request);

            logger.debug("Rule {} executed in {}ms, decision: {}",
                request.getRuleId(), response.getExecutionTimeMs(), response.getDecision());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Decision request failed for rule: {}", request.getRuleId(), e);

            // 返回错误响应
            DecisionResponse errorResponse = DecisionResponse.builder()
                .decision("REJECT")
                .reason("决策请求失败: " + e.getMessage())
                .executionTimeMs(0)
                .build();

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 通过规则Key执行决策（从缓存加载规则）
     * POST /api/v1/decide/{ruleKey}
     * 支持灰度分流，无需在请求体中传递脚本
     *
     * @param ruleKey 规则Key
     * @param request 决策请求（仅需 features，无需 script）
     * @return 决策响应
     */
    @PostMapping(
        value = "/decide/{ruleKey}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<DecisionResponse> decideByKey(
            @PathVariable String ruleKey,
            @RequestBody Map<String, Object> features) {
        logger.info("Received cache-aware decision request for ruleKey: {}", ruleKey);

        try {
            DecisionRequest request = new DecisionRequest();
            request.setRuleId(ruleKey);
            request.setFeatures(features);
            request.setTimeoutMs(50);

            DecisionResponse response = ruleExecutionService.decide(ruleKey, request);

            logger.debug("RuleKey {} executed in {}ms, decision: {}",
                ruleKey, response.getExecutionTimeMs(), response.getDecision());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Cache-aware decision request failed for ruleKey: {}", ruleKey, e);

            DecisionResponse errorResponse = DecisionResponse.builder()
                .decision("REJECT")
                .reason("决策请求失败: " + e.getMessage())
                .executionTimeMs(0)
                .build();

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    /**
     * 通过决策流Key执行决策
     * POST /api/v1/decision-flows/{flowKey}/execute
     */
    @PostMapping(
        value = "/decision-flows/{flowKey}/execute",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<DecisionResponse> decideByFlow(
            @PathVariable String flowKey,
            @RequestBody Map<String, Object> features) {
        logger.info("Received decision flow request for flowKey: {}", flowKey);
        try {
            DecisionResponse response = decisionFlowExecutionService.executeFlow(flowKey, features);
            logger.debug("DecisionFlow {} executed in {}ms, decision: {}",
                flowKey, response.getExecutionTimeMs(), response.getDecision());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Decision flow request failed for flowKey: {}", flowKey, e);
            DecisionResponse errorResponse = DecisionResponse.builder()
                .decision("REJECT")
                .reason("决策流执行失败: " + e.getMessage())
                .executionTimeMs(0)
                .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
