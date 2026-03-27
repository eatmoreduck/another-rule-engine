package com.example.ruleengine.service;

import com.example.ruleengine.engine.GroovyScriptEngine;
import com.example.ruleengine.exception.RuleExecutionException;
import com.example.ruleengine.model.DecisionRequest;
import com.example.ruleengine.model.DecisionResponse;
import com.example.ruleengine.model.FeatureRequest;
import com.example.ruleengine.model.FeatureResponse;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 规则执行服务
 * Source: RESEARCH.md 模式3 + CONTEXT.md 决策 D-11、D-12
 *
 * 功能：
 * 1. REXEC-01: 同步 API 50ms 内返回决策结果
 * 2. 整合 Groovy 脚本引擎和特征获取服务
 * 3. D-12: 使用独立线程池执行规则，隔离风险
 */
@Service
public class RuleExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(RuleExecutionService.class);

    private final GroovyScriptEngine scriptEngine;
    private final FeatureProviderService featureProvider;
    private final TimeLimiter timeLimiter;
    private final ThreadPoolTaskExecutor ruleExecutorPool;  // D-12: 独立线程池

    public RuleExecutionService(
        GroovyScriptEngine scriptEngine,
        FeatureProviderService featureProvider,
        TimeLimiter ruleExecutionTimeLimiter,
        ThreadPoolTaskExecutor ruleExecutorPool  // D-12: 注入独立线程池
    ) {
        this.scriptEngine = scriptEngine;
        this.featureProvider = featureProvider;
        this.timeLimiter = ruleExecutionTimeLimiter;
        this.ruleExecutorPool = ruleExecutorPool;  // D-12: 初始化线程池
    }

    /**
     * 执行规则决策
     * REXEC-01: 同步 API 接收决策请求，50ms 内返回结果
     * D-12: 使用独立线程池执行规则，隔离风险
     */
    public DecisionResponse executeDecision(DecisionRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // D-12: 使用独立线程池执行规则，隔离风险
            CompletableFuture<DecisionResponse> future = CompletableFuture.supplyAsync(
                () -> executeRuleInternal(request),
                ruleExecutorPool  // D-12: 指定使用独立线程池
            );

            // D-11: 超时控制（使用 CompletableFuture.get 超时机制）
            DecisionResponse response = future.get(request.getTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
            response.setExecutionTimeMs(System.currentTimeMillis() - startTime);

            logger.debug("Rule {} executed in {}ms", request.getRuleId(), response.getExecutionTimeMs());
            return response;

        } catch (java.util.concurrent.TimeoutException e) {
            // D-11: 超时返回降级决策
            long executionTime = System.currentTimeMillis() - startTime;
            logger.warn("Rule {} execution timeout after {}ms", request.getRuleId(), executionTime);

            return DecisionResponse.builder()
                .decision("REJECT")
                .reason("规则执行超时")
                .executionTimeMs(executionTime)
                .timeout(true)
                .build();

        } catch (Exception e) {
            // D-11: 异常返回降级决策
            long executionTime = System.currentTimeMillis() - startTime;
            logger.error("Rule {} execution failed after {}ms", request.getRuleId(), executionTime, e);

            return DecisionResponse.builder()
                .decision("REJECT")
                .reason("规则执行失败: " + e.getMessage())
                .executionTimeMs(executionTime)
                .timeout(false)
                .build();
        }
    }

    /**
     * 内部规则执行逻辑（在独立线程池中执行）
     */
    private DecisionResponse executeRuleInternal(DecisionRequest request) {
        try {
            // 1. 获取特征（REXEC-04: 三级策略）
            FeatureRequest featureRequest = new FeatureRequest(
                request.getFeatures(),
                request.getRequiredFeatures()
            );
            featureRequest.setTimeoutMs(Math.min(20, request.getTimeoutMs())); // 特征获取最多 20ms

            FeatureResponse featureResponse = featureProvider.getFeatures(featureRequest);
            Map<String, Object> features = featureResponse.getFeatures();

            // 2. 执行 Groovy 脚本（REXEC-03: Groovy DSL 动态加载执行）
            Object result = scriptEngine.executeScript(
                request.getRuleId(),
                request.getScript(),
                features
            );

            // 3. 构建决策响应
            return buildDecisionResponse(result, features);

        } catch (RuleExecutionException e) {
            logger.error("Rule execution failed for rule: {}", request.getRuleId(), e);
            throw e;  // 重新抛出，由外层处理
        }
    }

    /**
     * 构建决策响应
     * D-18: 响应格式 {decision, reason, executionTimeMs}
     */
    private DecisionResponse buildDecisionResponse(Object result, Map<String, Object> context) {
        DecisionResponse response = new DecisionResponse();

        // 解析脚本执行结果
        if (result instanceof Boolean) {
            response.setDecision(((Boolean) result) ? "PASS" : "REJECT");
            response.setReason("规则执行完成");
        } else if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            Object decision = resultMap.get("decision");
            Object reason = resultMap.get("reason");
            response.setDecision(decision != null ? decision.toString() : "REJECT");
            response.setReason(reason != null ? reason.toString() : "规则执行完成");
        } else if (result instanceof String) {
            response.setDecision((String) result);
            response.setReason("规则执行完成");
        } else {
            response.setDecision("REJECT");
            response.setReason("规则返回无效结果: " + result);
        }

        response.setExecutionContext(new HashMap<>(context));

        return response;
    }
}
