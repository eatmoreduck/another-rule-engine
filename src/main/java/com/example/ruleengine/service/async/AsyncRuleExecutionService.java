package com.example.ruleengine.service.async;

import com.example.ruleengine.cache.RuleCacheService;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.engine.GroovyScriptEngine;
import com.example.ruleengine.metrics.RuleExecutionMetrics;
import com.example.ruleengine.model.DecisionRequest;
import com.example.ruleengine.model.DecisionResponse;
import com.example.ruleengine.model.FeatureRequest;
import com.example.ruleengine.model.FeatureResponse;
import com.example.ruleengine.service.FeatureProviderService;
import com.example.ruleengine.service.executionlog.ExecutionLogService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 异步规则执行服务
 * REXEC-02: 异步事件驱动执行模式
 * REXEC-05: 超时控制与降级策略
 *
 * 功能：
 * 1. 使用 @Async + 独立线程池异步执行规则
 * 2. 50ms 超时控制，超时返回降级决策（PASS）
 * 3. CircuitBreaker 断路器保护
 * 4. 异步回调处理，结果存入 AsyncResultStore
 */
@Service
public class AsyncRuleExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncRuleExecutionService.class);

    /** 默认超时时间（毫秒） */
    private static final long DEFAULT_TIMEOUT_MS = 50;

    /** 降级决策 */
    private static final String DEGRADATION_DECISION = "PASS";
    private static final String DEGRADATION_REASON = "规则执行超时，返回默认通过决策";

    private final GroovyScriptEngine scriptEngine;
    private final FeatureProviderService featureProvider;
    private final ThreadPoolTaskExecutor ruleExecutorPool;
    private final RuleCacheService ruleCacheService;
    private final ExecutionLogService executionLogService;
    private final RuleExecutionMetrics ruleExecutionMetrics;
    private final AsyncResultStore asyncResultStore;
    private final CircuitBreaker circuitBreaker;

    public AsyncRuleExecutionService(
        GroovyScriptEngine scriptEngine,
        FeatureProviderService featureProvider,
        @Qualifier("ruleExecutorPool") ThreadPoolTaskExecutor ruleExecutorPool,
        RuleCacheService ruleCacheService,
        ExecutionLogService executionLogService,
        RuleExecutionMetrics ruleExecutionMetrics,
        AsyncResultStore asyncResultStore,
        @Qualifier("ruleExecutionCircuitBreaker") CircuitBreaker circuitBreaker
    ) {
        this.scriptEngine = scriptEngine;
        this.featureProvider = featureProvider;
        this.ruleExecutorPool = ruleExecutorPool;
        this.ruleCacheService = ruleCacheService;
        this.executionLogService = executionLogService;
        this.ruleExecutionMetrics = ruleExecutionMetrics;
        this.asyncResultStore = asyncResultStore;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * 异步执行规则决策
     * 使用 @Async 注解在独立线程池中执行
     * REXEC-02: 异步事件驱动执行
     * REXEC-05: 超时控制 + 降级策略
     *
     * @param request 决策请求
     * @return CompletableFuture 包含决策响应
     */
    public CompletableFuture<DecisionResponse> executeAsync(DecisionRequest request) {
        String requestId = UUID.randomUUID().toString();
        logger.info("提交异步决策请求: requestId={}, ruleId={}", requestId, request.getRuleId());

        CompletableFuture<DecisionResponse> future = CompletableFuture.supplyAsync(
            () -> executeWithCircuitBreaker(request),
            ruleExecutorPool
        );

        // 设置超时和回调
        return future
            .orTimeout(request.getTimeoutMs(), TimeUnit.MILLISECONDS)
            .handle((response, throwable) -> {
                DecisionResponse finalResponse;
                if (throwable != null) {
                    finalResponse = handleAsyncException(request, throwable);
                } else {
                    finalResponse = response;
                }
                // 存储结果
                asyncResultStore.storeResult(requestId, finalResponse);
                logger.info("异步决策完成: requestId={}, decision={}, time={}ms",
                    requestId, finalResponse.getDecision(), finalResponse.getExecutionTimeMs());
                return finalResponse;
            });
    }

    /**
     * 异步执行规则决策并立即返回 requestId
     * 用于 "提交后轮询" 模式
     *
     * @param request 决策请求
     * @return 请求ID，用于后续查询结果
     */
    public String submitAsync(DecisionRequest request) {
        String requestId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        logger.info("提交异步决策请求（轮询模式）: requestId={}, ruleId={}", requestId, request.getRuleId());

        CompletableFuture.supplyAsync(
            () -> executeWithTimeout(request, startTime),
            ruleExecutorPool
        )
        .orTimeout(request.getTimeoutMs(), TimeUnit.MILLISECONDS)
        .handle((response, throwable) -> {
            DecisionResponse finalResponse;
            if (throwable != null) {
                finalResponse = handleAsyncException(request, throwable);
                finalResponse.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            } else {
                finalResponse = response;
            }
            asyncResultStore.storeResult(requestId, finalResponse);
            logger.info("异步决策完成（轮询模式）: requestId={}, decision={}, time={}ms",
                requestId, finalResponse.getDecision(), finalResponse.getExecutionTimeMs());
            return finalResponse;
        });

        return requestId;
    }

    /**
     * 通过 ruleKey 从缓存加载规则并异步执行
     *
     * @param ruleKey 规则Key
     * @param request 决策请求
     * @return CompletableFuture 包含决策响应
     */
    @Async("ruleExecutorPool")
    public CompletableFuture<DecisionResponse> executeAsyncByRuleKey(String ruleKey, DecisionRequest request) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        try {
            // 从缓存加载规则
            Rule rule = ruleCacheService.getEnabledRule(ruleKey);
            if (rule == null) {
                DecisionResponse response = DecisionResponse.builder()
                    .decision("REJECT")
                    .reason("规则不存在或未启用")
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .timeout(false)
                    .build();
                asyncResultStore.storeResult(requestId, response);
                return CompletableFuture.completedFuture(response);
            }

            if (!rule.getEnabled() || rule.getDeleted()) {
                DecisionResponse response = DecisionResponse.builder()
                    .decision("REJECT")
                    .reason("规则未启用或已删除")
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .timeout(false)
                    .build();
                asyncResultStore.storeResult(requestId, response);
                return CompletableFuture.completedFuture(response);
            }

            request.setRuleId(rule.getRuleKey());
            request.setScript(rule.getGroovyScript());

            CompletableFuture<DecisionResponse> future = CompletableFuture.supplyAsync(
                () -> executeWithCircuitBreaker(request),
                ruleExecutorPool
            );

            DecisionResponse response = future.get(request.getTimeoutMs(), TimeUnit.MILLISECONDS);
            response.setExecutionTimeMs(System.currentTimeMillis() - startTime);

            asyncResultStore.storeResult(requestId, response);
            recordSuccess(ruleKey, request, response);

            return CompletableFuture.completedFuture(response);

        } catch (TimeoutException e) {
            DecisionResponse response = buildDegradationResponse(System.currentTimeMillis() - startTime);
            asyncResultStore.storeResult(requestId, response);
            recordTimeout(ruleKey, request, System.currentTimeMillis() - startTime);
            return CompletableFuture.completedFuture(response);

        } catch (Exception e) {
            DecisionResponse response = DecisionResponse.builder()
                .decision("REJECT")
                .reason("规则执行失败: " + e.getMessage())
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .timeout(false)
                .build();
            asyncResultStore.storeResult(requestId, response);
            recordError(ruleKey, request, System.currentTimeMillis() - startTime, e.getMessage());
            return CompletableFuture.completedFuture(response);
        }
    }

    /**
     * 查询异步执行结果
     *
     * @param requestId 请求ID
     * @return 决策响应，不存在时返回 null
     */
    public DecisionResponse getAsyncResult(String requestId) {
        return asyncResultStore.getResult(requestId);
    }

    // ==================== 内部方法 ====================

    /**
     * 通过 CircuitBreaker 执行规则
     * REXEC-05: 断路器保护
     */
    private DecisionResponse executeWithCircuitBreaker(DecisionRequest request) {
        try {
            return circuitBreaker.executeSupplier(() -> executeRuleInternal(request));
        } catch (CallNotPermittedException e) {
            logger.warn("断路器已打开，拒绝执行: ruleId={}", request.getRuleId());
            return DecisionResponse.builder()
                .decision(DEGRADATION_DECISION)
                .reason("断路器已打开，返回降级决策: " + e.getMessage())
                .executionTimeMs(0)
                .timeout(false)
                .build();
        } catch (Exception e) {
            logger.error("规则执行失败: ruleId={}", request.getRuleId(), e);
            throw e;
        }
    }

    /**
     * 带超时控制的执行
     */
    private DecisionResponse executeWithTimeout(DecisionRequest request, long startTime) {
        try {
            DecisionResponse response = executeWithCircuitBreaker(request);
            response.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            return response;
        } catch (Exception e) {
            return DecisionResponse.builder()
                .decision("REJECT")
                .reason("规则执行失败: " + e.getMessage())
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .timeout(false)
                .build();
        }
    }

    /**
     * 内部规则执行逻辑
     */
    private DecisionResponse executeRuleInternal(DecisionRequest request) {
        // 1. 获取特征
        FeatureRequest featureRequest = new FeatureRequest(
            request.getFeatures(),
            request.getRequiredFeatures()
        );
        featureRequest.setTimeoutMs(Math.min(20, request.getTimeoutMs()));

        FeatureResponse featureResponse = featureProvider.getFeatures(featureRequest);
        Map<String, Object> features = featureResponse.getFeatures();

        // 2. 执行 Groovy 脚本
        Object result = scriptEngine.executeScript(
            request.getRuleId(),
            request.getScript(),
            features
        );

        // 3. 构建决策响应
        return buildDecisionResponse(result, features);
    }

    /**
     * 异常处理：超时返回 PASS（降级策略），其他异常返回 REJECT
     * REXEC-05: 降级策略
     */
    private DecisionResponse handleAsyncException(DecisionRequest request, Throwable throwable) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        long executionTime = 0;

        if (cause instanceof TimeoutException || cause instanceof java.util.concurrent.TimeoutException) {
            logger.warn("异步规则执行超时: ruleId={}", request.getRuleId());
            recordTimeout(request.getRuleId(), request, executionTime);
            return buildDegradationResponse(executionTime);
        }

        if (cause instanceof CallNotPermittedException) {
            logger.warn("断路器已打开: ruleId={}", request.getRuleId());
            return buildDegradationResponse(executionTime);
        }

        logger.error("异步规则执行失败: ruleId={}", request.getRuleId(), cause);
        recordError(request.getRuleId(), request, executionTime, cause.getMessage());
        return DecisionResponse.builder()
            .decision("REJECT")
            .reason("规则执行失败: " + cause.getMessage())
            .executionTimeMs(executionTime)
            .timeout(false)
            .build();
    }

    /**
     * 构建降级响应（REXEC-05: 超时返回 PASS）
     */
    private DecisionResponse buildDegradationResponse(long executionTimeMs) {
        return DecisionResponse.builder()
            .decision(DEGRADATION_DECISION)
            .reason(DEGRADATION_REASON)
            .executionTimeMs(executionTimeMs)
            .timeout(true)
            .build();
    }

    /**
     * 构建决策响应
     */
    private DecisionResponse buildDecisionResponse(Object result, Map<String, Object> context) {
        DecisionResponse response = new DecisionResponse();

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

    private void recordSuccess(String ruleKey, DecisionRequest request, DecisionResponse response) {
        executionLogService.logSuccess(
            ruleKey, null, request.getFeatures(),
            response.getDecision(), response.getReason(),
            response.getExecutionTimeMs());
        ruleExecutionMetrics.recordExecution(
            ruleKey, response.getDecision(), response.getExecutionTimeMs());
    }

    private void recordTimeout(String ruleKey, DecisionRequest request, long executionTime) {
        executionLogService.logTimeout(ruleKey, null, request.getFeatures(), executionTime);
        ruleExecutionMetrics.recordError(ruleKey, new TimeoutException("异步执行超时"));
    }

    private void recordError(String ruleKey, DecisionRequest request, long executionTime, String message) {
        executionLogService.logError(
            ruleKey, null, request.getFeatures(), executionTime, message);
        ruleExecutionMetrics.recordError(ruleKey, new RuntimeException(message));
    }
}
