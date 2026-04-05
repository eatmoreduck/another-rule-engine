package com.example.ruleengine.service;

import com.example.ruleengine.cache.RuleCacheService;
import com.example.ruleengine.constants.VersionStatus;
import com.example.ruleengine.domain.GrayscaleConfig;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.domain.RuleVersion;
import com.example.ruleengine.engine.GroovyScriptEngine;
import com.example.ruleengine.exception.RuleExecutionException;
import com.example.ruleengine.metrics.RuleExecutionMetrics;
import com.example.ruleengine.model.DecisionRequest;
import com.example.ruleengine.model.DecisionResponse;
import com.example.ruleengine.model.FeatureRequest;
import com.example.ruleengine.model.FeatureResponse;
import com.example.ruleengine.repository.RuleVersionRepository;
import com.example.ruleengine.service.executionlog.ExecutionLogService;
import com.example.ruleengine.service.grayscale.GrayscaleService;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 规则执行服务
 * Source: RESEARCH.md 模式3 + CONTEXT.md 决策 D-11、D-12
 *
 * 功能：
 * 1. REXEC-01: 同步 API 50ms 内返回决策结果
 * 2. 整合 Groovy 脚本引擎和特征获取服务
 * 3. D-12: 使用独立线程池执行规则，隔离风险
 * 4. 从缓存加载数据库中的规则并执行（三级缓存策略）
 */
@Service
public class RuleExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(RuleExecutionService.class);

    private final GroovyScriptEngine scriptEngine;
    private final FeatureProviderService featureProvider;
    private final TimeLimiter timeLimiter;
    private final ThreadPoolTaskExecutor ruleExecutorPool;  // D-12: 独立线程池
    private final RuleCacheService ruleCacheService;  // 规则缓存服务
    private final ExecutionLogService executionLogService;  // 执行日志服务
    private final RuleExecutionMetrics ruleExecutionMetrics;  // MON-01: 执行监控指标
    private final GrayscaleService grayscaleService;  // VER-03: 灰度发布服务
    private final RuleVersionRepository ruleVersionRepository;  // 规则版本数据访问

    public RuleExecutionService(
        GroovyScriptEngine scriptEngine,
        FeatureProviderService featureProvider,
        TimeLimiter ruleExecutionTimeLimiter,
        ThreadPoolTaskExecutor ruleExecutorPool,  // D-12: 注入独立线程池
        RuleCacheService ruleCacheService,  // 注入规则缓存服务
        ExecutionLogService executionLogService,  // 注入执行日志服务
        RuleExecutionMetrics ruleExecutionMetrics,  // MON-01: 注入执行监控指标
        GrayscaleService grayscaleService,  // VER-03: 注入灰度发布服务
        RuleVersionRepository ruleVersionRepository  // 注入规则版本数据访问
    ) {
        this.scriptEngine = scriptEngine;
        this.featureProvider = featureProvider;
        this.timeLimiter = ruleExecutionTimeLimiter;
        this.ruleExecutorPool = ruleExecutorPool;  // D-12: 初始化线程池
        this.ruleCacheService = ruleCacheService;
        this.executionLogService = executionLogService;
        this.ruleExecutionMetrics = ruleExecutionMetrics;
        this.grayscaleService = grayscaleService;
        this.ruleVersionRepository = ruleVersionRepository;
    }

    /**
     * 执行规则决策（从缓存加载规则）
     * REXEC-01: 同步 API 接收决策请求，50ms 内返回结果
     * D-12: 使用独立线程池执行规则，隔离风险
     *
     * @param ruleKey 规则Key
     * @param request 决策请求
     * @return 决策响应
     */
    public DecisionResponse decide(String ruleKey, DecisionRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 从缓存加载规则（L2: 规则元数据缓存）
            Rule rule = ruleCacheService.getEnabledRule(ruleKey);

            if (rule == null) {
                long executionTime = System.currentTimeMillis() - startTime;
                logger.warn("规则不存在或未启用: ruleKey={}", ruleKey);
                return DecisionResponse.builder()
                    .decision("REJECT")
                    .reason("规则不存在或未启用")
                    .executionTimeMs(executionTime)
                    .timeout(false)
                    .build();
            }

            if (!rule.getEnabled() || rule.getDeleted()) {
                long executionTime = System.currentTimeMillis() - startTime;
                logger.warn("规则未启用或已删除: ruleKey={}, enabled={}, deleted={}", ruleKey, rule.getEnabled(), rule.getDeleted());
                return DecisionResponse.builder()
                    .decision("REJECT")
                    .reason("规则未启用或已删除")
                    .executionTimeMs(executionTime)
                    .timeout(false)
                    .build();
            }

            // 2. 设置规则脚本到请求中（支持灰度分流）
            request.setRuleId(rule.getRuleKey());
            resolveGrayscaleScript(ruleKey, rule, request);

            // 3. 使用独立线程池执行规则
            CompletableFuture<DecisionResponse> future = CompletableFuture.supplyAsync(
                () -> executeRuleInternal(request),
                ruleExecutorPool
            );

            // 4. 超时控制
            DecisionResponse response = future.get(request.getTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
            response.setExecutionTimeMs(System.currentTimeMillis() - startTime);

            logger.debug("Rule {} executed in {}ms", ruleKey, response.getExecutionTimeMs());

            // MON-02: 异步记录执行成功日志
            executionLogService.logSuccess(
                ruleKey, null, request.getFeatures(),
                response.getDecision(), response.getReason(),
                response.getExecutionTimeMs());

            // MON-01: 记录执行指标
            ruleExecutionMetrics.recordExecution(
                ruleKey, response.getDecision(), response.getExecutionTimeMs());

            return response;

        } catch (java.util.concurrent.TimeoutException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logger.warn("Rule {} execution timeout after {}ms", ruleKey, executionTime);

            // MON-02: 异步记录超时日志
            executionLogService.logTimeout(
                ruleKey, null, request.getFeatures(), executionTime);

            // MON-01: 记录错误指标
            ruleExecutionMetrics.recordError(ruleKey, e);

            return DecisionResponse.builder()
                .decision("REJECT")
                .reason("规则执行超时")
                .executionTimeMs(executionTime)
                .timeout(true)
                .build();

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logger.error("Rule {} execution failed after {}ms", ruleKey, executionTime, e);

            // MON-02: 异步记录错误日志
            executionLogService.logError(
                ruleKey, null, request.getFeatures(),
                executionTime, e.getMessage());

            // MON-01: 记录错误指标
            ruleExecutionMetrics.recordError(ruleKey, e);

            return DecisionResponse.builder()
                .decision("REJECT")
                .reason("规则执行失败: " + e.getMessage())
                .executionTimeMs(executionTime)
                .timeout(false)
                .build();
        }
    }

    /**
     * 执行规则决策（直接执行脚本）
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

            // MON-02: 异步记录执行成功日志
            executionLogService.logSuccess(
                request.getRuleId(), null, request.getFeatures(),
                response.getDecision(), response.getReason(),
                response.getExecutionTimeMs());

            // MON-01: 记录执行指标
            ruleExecutionMetrics.recordExecution(
                request.getRuleId(), response.getDecision(), response.getExecutionTimeMs());

            return response;

        } catch (java.util.concurrent.TimeoutException e) {
            // D-11: 超时返回降级决策
            long executionTime = System.currentTimeMillis() - startTime;
            logger.warn("Rule {} execution timeout after {}ms", request.getRuleId(), executionTime);

            // MON-02: 异步记录超时日志
            executionLogService.logTimeout(
                request.getRuleId(), null, request.getFeatures(), executionTime);

            // MON-01: 记录错误指标
            ruleExecutionMetrics.recordError(request.getRuleId(), e);

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

            // MON-02: 异步记录错误日志
            executionLogService.logError(
                request.getRuleId(), null, request.getFeatures(),
                executionTime, e.getMessage());

            // MON-01: 记录错误指标
            ruleExecutionMetrics.recordError(request.getRuleId(), e);

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

    /**
     * 灰度分流：检查是否有运行中的灰度配置，使用多策略匹配决定使用哪个版本的脚本
     * VER-03: 灰度发布，Phase 2 升级为多策略匹配
     *
     * @param ruleKey 规则Key
     * @param rule    当前规则
     * @param request 决策请求
     */
    private void resolveGrayscaleScript(String ruleKey, Rule rule,
                                         DecisionRequest request) {
        try {
            // 使用请求中的 features 进行多策略匹配
            Map<String, Object> features = request.getFeatures() != null
                    ? request.getFeatures() : Map.of();

            Optional<Integer> grayscaleVersionOpt =
                    grayscaleService.resolveGrayscaleVersion(ruleKey, features);

            if (grayscaleVersionOpt.isPresent()) {
                int grayscaleVersion = grayscaleVersionOpt.get();
                logger.info("灰度分流命中: ruleKey={}, 使用灰度版本={}",
                        ruleKey, grayscaleVersion);

                // 从版本历史加载灰度版本的脚本
                Optional<String> canaryScript = loadCanaryScript(ruleKey, grayscaleVersion);
                if (canaryScript.isPresent()) {
                    request.setScript(canaryScript.get());
                } else {
                    // 灰度版本脚本加载失败，fallback 到当前版本
                    logger.warn("灰度版本脚本加载失败, fallback 到当前版本: ruleKey={}, version={}",
                            ruleKey, grayscaleVersion);
                    request.setScript(rule.getGroovyScript());
                }

                // 记录灰度指标
                Optional<GrayscaleConfig> runningConfig =
                        grayscaleService.getRunningConfig(ruleKey);
                runningConfig.ifPresent(config ->
                        recordGrayscaleMetricsAsync(config.getId(),
                                grayscaleVersion, System.currentTimeMillis(), true));
            } else {
                // 使用当前版本脚本
                request.setScript(rule.getGroovyScript());

                // 如果有运行中的灰度配置，记录当前版本指标
                Optional<GrayscaleConfig> runningConfig =
                        grayscaleService.getRunningConfig(ruleKey);
                runningConfig.ifPresent(config -> {
                    long startTime = System.currentTimeMillis();
                    recordGrayscaleMetricsAsync(config.getId(),
                            config.getCurrentVersion(), startTime, true);
                });
            }
        } catch (Exception e) {
            // 灰度服务异常，fallback 到当前版本脚本
            logger.error("灰度分流异常, fallback 到当前版本: ruleKey={}", ruleKey, e);
            request.setScript(rule.getGroovyScript());
        }
    }

    /**
     * 从版本历史加载灰度版本的 Groovy 脚本
     * 优先按 CANARY 状态查找，其次按版本号查找
     */
    private Optional<String> loadCanaryScript(String ruleKey, int version) {
        try {
            // 优先从 CANARY 状态的版本中查找
            Optional<RuleVersion> canaryVersion =
                    ruleVersionRepository.findTopByRuleKeyAndStatusOrderByVersionDesc(
                            ruleKey, VersionStatus.CANARY);
            if (canaryVersion.isPresent()) {
                return Optional.of(canaryVersion.get().getGroovyScript());
            }

            // 兜底：按版本号查找
            return ruleVersionRepository.findByRuleKeyAndVersion(ruleKey, version)
                    .map(RuleVersion::getGroovyScript);
        } catch (Exception e) {
            logger.error("加载灰度版本脚本失败: ruleKey={}, version={}", ruleKey, version, e);
            return Optional.empty();
        }
    }

    /**
     * 异步记录灰度指标
     */
    private void recordGrayscaleMetricsAsync(Long configId, Integer version,
                                              long startTime, boolean isSuccess) {
        long executionTime = System.currentTimeMillis() - startTime;
        try {
            grayscaleService.recordMetrics(
                    configId, version, executionTime, isSuccess);
        } catch (Exception e) {
            logger.warn("记录灰度指标失败: configId={}, version={}",
                    configId, version, e);
        }
    }
}
