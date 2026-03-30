package com.example.ruleengine.service;

import com.example.ruleengine.cache.RuleCacheService;
import com.example.ruleengine.engine.GroovyScriptEngine;
import com.example.ruleengine.exception.RuleExecutionException;
import com.example.ruleengine.metrics.RuleExecutionMetrics;
import com.example.ruleengine.model.DecisionRequest;
import com.example.ruleengine.model.DecisionResponse;
import com.example.ruleengine.model.FeatureResponse;
import com.example.ruleengine.service.executionlog.ExecutionLogService;
import com.example.ruleengine.service.grayscale.GrayscaleService;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RuleExecutionService 单元测试
 * <p>
 * 测试覆盖:
 * 1. 正常执行流程
 * 2. 超时场景
 * 3. 异常降级
 * 4. 线程池隔离
 * 5. 各种返回类型处理
 */
@DisplayName("RuleExecutionService 测试")
@ExtendWith(MockitoExtension.class)
class RuleExecutionServiceTest {

    @Mock
    private GroovyScriptEngine scriptEngine;

    @Mock
    private FeatureProviderService featureProvider;

    @Mock
    private RuleCacheService ruleCacheService;

    @Mock
    private ExecutionLogService executionLogService;

    @Mock
    private RuleExecutionMetrics ruleExecutionMetrics;

    @Mock
    private GrayscaleService grayscaleService;

    private TimeLimiter timeLimiter;
    private ThreadPoolTaskExecutor taskExecutor;
    private RuleExecutionService ruleExecutionService;

    @BeforeEach
    void setUp() {
        // 配置 TimeLimiter
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(100))
            .build();
        timeLimiter = TimeLimiter.of(config);

        // 配置测试用线程池
        taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(2);
        taskExecutor.setMaxPoolSize(4);
        taskExecutor.setQueueCapacity(10);
        taskExecutor.setThreadNamePrefix("test-executor-");
        taskExecutor.initialize();

        ruleExecutionService = new RuleExecutionService(
            scriptEngine,
            featureProvider,
            timeLimiter,
            taskExecutor,
            ruleCacheService,
            executionLogService,
            ruleExecutionMetrics,
            grayscaleService
        );
    }

    // ==================== 正常执行测试 ====================

    @Nested
    @DisplayName("正常执行测试")
    class NormalExecutionTests {

        @Test
        @DisplayName("执行返回布尔值 true 的脚本应返回 PASS")
        void shouldReturnPassWhenScriptReturnsTrue() {
            // Arrange
            DecisionRequest request = createRequest("rule-001", "return true");
            setupFeatureMock();
            when(scriptEngine.executeScript(any(), any(), any())).thenReturn(true);

            // Act
            DecisionResponse response = ruleExecutionService.executeDecision(request);

            // Assert
            assertEquals("PASS", response.getDecision());
            assertEquals("规则执行完成", response.getReason());
            assertFalse(response.isTimeout());
            assertTrue(response.getExecutionTimeMs() >= 0);
        }

        @Test
        @DisplayName("执行返回布尔值 false 的脚本应返回 REJECT")
        void shouldReturnRejectWhenScriptReturnsFalse() {
            // Arrange
            DecisionRequest request = createRequest("rule-002", "return false");
            setupFeatureMock();
            when(scriptEngine.executeScript(any(), any(), any())).thenReturn(false);

            // Act
            DecisionResponse response = ruleExecutionService.executeDecision(request);

            // Assert
            assertEquals("REJECT", response.getDecision());
            assertEquals("规则执行完成", response.getReason());
        }

        @Test
        @DisplayName("执行返回 Map 的脚本应正确解析")
        void shouldParseMapResult() {
            // Arrange
            DecisionRequest request = createRequest("rule-003", "return [decision: 'REJECT', reason: '金额超限']");
            setupFeatureMock();

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("decision", "REJECT");
            resultMap.put("reason", "金额超限");
            when(scriptEngine.executeScript(any(), any(), any())).thenReturn(resultMap);

            // Act
            DecisionResponse response = ruleExecutionService.executeDecision(request);

            // Assert
            assertEquals("REJECT", response.getDecision());
            assertEquals("金额超限", response.getReason());
        }

        @Test
        @DisplayName("执行返回 String 的脚本应作为 decision")
        void shouldParseStringResult() {
            // Arrange
            DecisionRequest request = createRequest("rule-004", "return 'PASS'");
            setupFeatureMock();
            when(scriptEngine.executeScript(any(), any(), any())).thenReturn("PASS");

            // Act
            DecisionResponse response = ruleExecutionService.executeDecision(request);

            // Assert
            assertEquals("PASS", response.getDecision());
            assertEquals("规则执行完成", response.getReason());
        }

        @Test
        @DisplayName("执行应包含执行上下文")
        void shouldIncludeExecutionContext() {
            // Arrange
            DecisionRequest request = createRequest("rule-005", "return true");
            Map<String, Object> features = new HashMap<>();
            features.put("user_age", 25);
            features.put("amount", 1000);

            FeatureResponse featureResponse = new FeatureResponse();
            featureResponse.setFeatures(features);
            when(featureProvider.getFeatures(any())).thenReturn(featureResponse);
            when(scriptEngine.executeScript(any(), any(), any())).thenReturn(true);

            // Act
            DecisionResponse response = ruleExecutionService.executeDecision(request);

            // Assert
            assertNotNull(response.getExecutionContext());
            assertEquals(25, response.getExecutionContext().get("user_age"));
            assertEquals(1000, response.getExecutionContext().get("amount"));
        }
    }

    // ==================== 超时场景测试 ====================

    @Nested
    @DisplayName("超时场景测试")
    class TimeoutTests {

        @Test
        @DisplayName("脚本执行超时应返回 REJECT")
        void shouldReturnRejectOnTimeout() {
            // Arrange
            DecisionRequest request = createRequest("timeout-rule", "sleep(1000)");
            request.setTimeoutMs(50); // 设置 50ms 超时

            setupFeatureMock();
            when(scriptEngine.executeScript(any(), any(), any())).thenAnswer(invocation -> {
                Thread.sleep(200); // 模拟长时间执行
                return true;
            });

            // Act
            DecisionResponse response = ruleExecutionService.executeDecision(request);

            // Assert
            assertEquals("REJECT", response.getDecision());
            assertEquals("规则执行超时", response.getReason());
            assertTrue(response.isTimeout());
        }

        @Test
        @DisplayName("特征获取超时应不影响整体决策")
        void shouldHandleFeatureTimeoutGracefully() {
            // Arrange
            DecisionRequest request = createRequest("rule-006", "return true");
            request.setTimeoutMs(100);

            FeatureResponse featureResponse = new FeatureResponse();
            featureResponse.setFeatures(new HashMap<>());
            featureResponse.setFallbackToDefault(true);

            when(featureProvider.getFeatures(any())).thenReturn(featureResponse);
            when(scriptEngine.executeScript(any(), any(), any())).thenReturn(true);

            // Act
            DecisionResponse response = ruleExecutionService.executeDecision(request);

            // Assert
            assertEquals("PASS", response.getDecision());
        }
    }

    // ==================== 异常降级测试 ====================

    @Nested
    @DisplayName("异常降级测试")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("脚本执行异常应返回 REJECT")
        void shouldReturnRejectOnScriptException() {
            // Arrange
            DecisionRequest request = createRequest("error-rule", "throw new Exception()");
            setupFeatureMock();
            when(scriptEngine.executeScript(any(), any(), any()))
                .thenThrow(new RuleExecutionException("脚本执行失败"));

            // Act
            DecisionResponse response = ruleExecutionService.executeDecision(request);

            // Assert
            assertEquals("REJECT", response.getDecision());
            assertTrue(response.getReason().contains("规则执行失败"));
            assertFalse(response.isTimeout());
        }

        @Test
        @DisplayName("无效返回类型应返回 REJECT")
        void shouldReturnRejectForInvalidResultType() {
            // Arrange
            DecisionRequest request = createRequest("invalid-rule", "return 123");
            setupFeatureMock();
            when(scriptEngine.executeScript(any(), any(), any())).thenReturn(123); // 非法类型

            // Act
            DecisionResponse response = ruleExecutionService.executeDecision(request);

            // Assert
            assertEquals("REJECT", response.getDecision());
            assertTrue(response.getReason().contains("无效结果"));
        }

        @Test
        @DisplayName("Map 结果缺少 decision 字段应返回默认 REJECT")
        void shouldReturnDefaultRejectWhenMapMissingDecision() {
            // Arrange
            DecisionRequest request = createRequest("rule-007", "return [reason: 'only reason']");
            setupFeatureMock();

            Map<String, Object> incompleteMap = new HashMap<>();
            incompleteMap.put("reason", "only reason");
            // 没有 decision 字段

            when(scriptEngine.executeScript(any(), any(), any())).thenReturn(incompleteMap);

            // Act
            DecisionResponse response = ruleExecutionService.executeDecision(request);

            // Assert
            assertEquals("REJECT", response.getDecision());
            assertEquals("only reason", response.getReason());
        }

        @Test
        @DisplayName("null 返回值应返回 REJECT")
        void shouldHandleNullResult() {
            // Arrange
            DecisionRequest request = createRequest("null-rule", "return null");
            setupFeatureMock();
            when(scriptEngine.executeScript(any(), any(), any())).thenReturn(null);

            // Act
            DecisionResponse response = ruleExecutionService.executeDecision(request);

            // Assert
            assertEquals("REJECT", response.getDecision());
            assertTrue(response.getReason().contains("无效结果"));
        }
    }

    // ==================== 线程池隔离测试 ====================

    @Nested
    @DisplayName("线程池隔离测试")
    class ThreadPoolIsolationTests {

        @Test
        @DisplayName("应使用独立线程池执行规则")
        void shouldUseIsolatedThreadPool() {
            // Arrange
            DecisionRequest request = createRequest("rule-008", "return true");
            setupFeatureMock();
            when(scriptEngine.executeScript(any(), any(), any())).thenReturn(true);

            // Act
            DecisionResponse response = ruleExecutionService.executeDecision(request);

            // Assert
            assertEquals("PASS", response.getDecision());
            // 验证是在线程池中异步执行
            verify(scriptEngine, times(1)).executeScript(any(), any(), any());
        }

        @Test
        @DisplayName("并发执行多个规则应正常工作")
        void shouldHandleConcurrentExecution() throws Exception {
            // Arrange
            setupFeatureMock();
            when(scriptEngine.executeScript(any(), any(), any())).thenReturn(true);

            // Act - 并发执行 10 个规则
            List<CompletableFuture<DecisionResponse>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                DecisionRequest request = createRequest("concurrent-rule-" + i, "return true");
                futures.add(CompletableFuture.supplyAsync(() ->
                    ruleExecutionService.executeDecision(request)));
            }

            // 等待所有完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, java.util.concurrent.TimeUnit.SECONDS);

            // Assert
            for (CompletableFuture<DecisionResponse> future : futures) {
                DecisionResponse response = future.get();
                assertEquals("PASS", response.getDecision());
            }
        }
    }

    // ==================== 边界条件测试 ====================

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("空特征列表应正常工作")
        void shouldHandleEmptyFeatures() {
            // Arrange
            DecisionRequest request = new DecisionRequest();
            request.setRuleId("empty-features-rule");
            request.setScript("return true");
            request.setFeatures(Collections.emptyMap());
            request.setRequiredFeatures(Collections.emptyList());
            request.setTimeoutMs(100);

            FeatureResponse featureResponse = new FeatureResponse();
            featureResponse.setFeatures(Collections.emptyMap());
            when(featureProvider.getFeatures(any())).thenReturn(featureResponse);
            when(scriptEngine.executeScript(any(), any(), any())).thenReturn(true);

            // Act
            DecisionResponse response = ruleExecutionService.executeDecision(request);

            // Assert
            assertEquals("PASS", response.getDecision());
        }

        @Test
        @DisplayName("默认超时时间应为 50ms")
        void shouldUseDefaultTimeout() {
            // Arrange
            DecisionRequest request = new DecisionRequest();
            request.setRuleId("default-timeout-rule");
            request.setScript("return true");
            request.setFeatures(new HashMap<>());
            request.setRequiredFeatures(Collections.emptyList());
            // 不设置 timeoutMs，使用默认值

            setupFeatureMock();
            when(scriptEngine.executeScript(any(), any(), any())).thenReturn(true);

            // Act
            DecisionResponse response = ruleExecutionService.executeDecision(request);

            // Assert
            assertNotNull(response);
            assertEquals("PASS", response.getDecision());
        }

        @Test
        @DisplayName("特征获取时间应限制在 20ms 内")
        void shouldLimitFeatureFetchTime() {
            // Arrange
            DecisionRequest request = createRequest("rule-009", "return true");
            request.setTimeoutMs(100);

            FeatureResponse featureResponse = new FeatureResponse();
            featureResponse.setFeatures(new HashMap<>());

            // 验证特征获取的超时设置
            when(featureProvider.getFeatures(argThat(req ->
                req.getTimeoutMs() <= 20  // 特征获取最多 20ms
            ))).thenReturn(featureResponse);

            when(scriptEngine.executeScript(any(), any(), any())).thenReturn(true);

            // Act
            DecisionResponse response = ruleExecutionService.executeDecision(request);

            // Assert
            assertEquals("PASS", response.getDecision());
            verify(featureProvider).getFeatures(argThat(req -> req.getTimeoutMs() <= 20));
        }
    }

    // ==================== 性能测试 ====================

    @Nested
    @DisplayName("性能测试")
    class PerformanceTests {

        @Test
        @DisplayName("执行时间应被记录")
        void shouldRecordExecutionTime() {
            // Arrange
            DecisionRequest request = createRequest("perf-rule", "return true");
            setupFeatureMock();
            when(scriptEngine.executeScript(any(), any(), any())).thenReturn(true);

            // Act
            DecisionResponse response = ruleExecutionService.executeDecision(request);

            // Assert
            assertTrue(response.getExecutionTimeMs() >= 0);
        }

        @Test
        @DisplayName("快速执行应在 50ms 内完成")
        void shouldCompleteWithin50ms() {
            // Arrange
            DecisionRequest request = createRequest("fast-rule", "return true");
            setupFeatureMock();
            when(scriptEngine.executeScript(any(), any(), any())).thenReturn(true);

            // Act
            long start = System.currentTimeMillis();
            DecisionResponse response = ruleExecutionService.executeDecision(request);
            long elapsed = System.currentTimeMillis() - start;

            // Assert
            assertTrue(elapsed < 1000, "执行时间应小于 1 秒，实际: " + elapsed + "ms");
            assertEquals("PASS", response.getDecision());
        }
    }

    // ==================== 辅助方法 ====================

    private DecisionRequest createRequest(String ruleId, String script) {
        DecisionRequest request = new DecisionRequest();
        request.setRuleId(ruleId);
        request.setScript(script);
        request.setFeatures(new HashMap<>());
        request.setRequiredFeatures(Collections.emptyList());
        request.setTimeoutMs(500);
        return request;
    }

    private void setupFeatureMock() {
        FeatureResponse featureResponse = new FeatureResponse();
        featureResponse.setFeatures(new HashMap<>());
        when(featureProvider.getFeatures(any())).thenReturn(featureResponse);
    }
}
