package com.example.ruleengine.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.ruleengine.model.DecisionRequest;
import com.example.ruleengine.model.DecisionResponse;
import com.example.ruleengine.service.async.AsyncRuleExecutionService;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 异步决策 API 控制器
 * REXEC-02: 异步事件驱动执行
 *
 * 功能：
 * 1. POST /api/v1/decide/async - 提交异步决策请求
 * 2. GET /api/v1/decide/async/{requestId} - 查询异步决策结果
 */
@RestController
@RequestMapping("/api/v1/decide/async")
@Timed(value = "decision.async.api", description = "Async Decision API performance")
public class AsyncDecisionController {

    private static final Logger logger = LoggerFactory.getLogger(AsyncDecisionController.class);

    private final AsyncRuleExecutionService asyncRuleExecutionService;

    public AsyncDecisionController(AsyncRuleExecutionService asyncRuleExecutionService) {
        this.asyncRuleExecutionService = asyncRuleExecutionService;
    }

    /**
     * 提交异步决策请求
     * 立即返回 requestId，客户端通过轮询查询结果
     *
     * @param request 决策请求
     * @return 包含 requestId 和 PROCESSING 状态的响应
     */
    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @SaCheckPermission("api:decision:execute")
    public ResponseEntity<Map<String, String>> submitAsyncDecision(
        @Valid @RequestBody DecisionRequest request
    ) {
        logger.info("收到异步决策请求: ruleId={}", request.getRuleId());

        String requestId = asyncRuleExecutionService.submitAsync(request);

        Map<String, String> response = new HashMap<>();
        response.put("requestId", requestId);
        response.put("status", "PROCESSING");

        return ResponseEntity.accepted().body(response);
    }

    /**
     * 查询异步决策结果
     *
     * @param requestId 请求ID
     * @return 决策结果（如果已完成）或 PROCESSING 状态（如果仍在执行中）
     */
    @GetMapping(
        value = "/{requestId}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @SaCheckPermission("api:decision:execute")
    public ResponseEntity<Map<String, Object>> getAsyncResult(
        @PathVariable String requestId
    ) {
        logger.debug("查询异步决策结果: requestId={}", requestId);

        DecisionResponse result = asyncRuleExecutionService.getAsyncResult(requestId);

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);

        if (result != null) {
            response.put("status", "COMPLETED");
            response.put("decision", result.getDecision());
            response.put("reason", result.getReason());
            response.put("executionTimeMs", result.getExecutionTimeMs());
            response.put("timeout", result.isTimeout());
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "PROCESSING");
            return ResponseEntity.ok(response);
        }
    }
}
