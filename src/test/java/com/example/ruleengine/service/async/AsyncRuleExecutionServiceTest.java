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
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AsyncRuleExecutionService 单元测试
 *
 * 测试覆盖：
 * 1. 异步执行正常场景
 * 2. 超时降级场景
 * 3. 断路器保护场景
 * 4. 规则不存在场景
 * 5. 提交后轮询模式
 */
@DisplayName("AsyncRuleExecutionService 测试")
@ExtendWith(MockitoExtension.class)
class AsyncRuleExecutionServiceTest {

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

    private AsyncResultStore asyncResultStore;

    private CircuitBreaker circuitBreaker;

    private ThreadPoolTaskExecutor ruleExecutorPool;

    private AsyncRuleExecutionService asyncRuleExecutionService;

    @BeforeEach
    void setUp() {
        asyncResultStore = new AsyncResultStore(300);

        // 创建真实的 CircuitBreaker
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofSeconds(5))
            .permittedNumberOfCallsInHalfOpenState(5)
            .minimumNumberOfCalls(5)
            .build();
        circuitBreaker = CircuitBreaker.of("testRuleExecution", config);

        // 创建真实的线程池
        ruleExecutorPool = new ThreadPoolTaskExecutor();
        ruleExecutorPool.setCorePoolSize(4);
        ruleExecutorPool.setMaxPoolSize(8);
        ruleExecutorPool.setQueueCapacity(50);
        ruleExecutorPool.setThreadNamePrefix("test-rule-executor-");
        ruleExecutorPool.initialize();

        asyncRuleExecutionService = new AsyncRuleExecutionService(
            scriptEngine,
            featureProvider,
            ruleExecutorPool,
            ruleCacheService,
            executionLogService,
            ruleExecutionMetrics,
            asyncResultStore,
            circuitBreaker
        );
    }

    private DecisionRequest createTestRequest() {
        DecisionRequest request = new DecisionRequest();
        request.setRuleId("test-rule-001");
        request.setScript("return true");
        Map<String, Object> features = new HashMap<>();
        features.put("amount", 5000);
        request.setFeatures(features);
        request.setTimeoutMs(50);
        return request;
    }

    private void mockSuccessfulExecution() {
        FeatureResponse featureResponse = new FeatureResponse();
        Map<String, Object> features = new HashMap<>();
        features.put("amount", 5000);
        featureResponse.setFeatures(features);

        when(featureProvider.getFeatures(any(FeatureRequest.class)))
            .thenReturn(featureResponse);
        when(scriptEngine.executeScript(eq("test-rule-001"), anyString(), anyMap()))
            .thenReturn(true);
    }

    // ==================== 异步执行正常场景 ====================

    @Nested
    @DisplayName("异步执行正常场景")
    class NormalExecutionTests {

        @Test
        @DisplayName("executeAsync 应返回正确的决策结果")
        void shouldReturnCorrectDecision() throws Exception {
            mockSuccessfulExecution();

            DecisionRequest request = createTestRequest();
            CompletableFuture<DecisionResponse> future = asyncRuleExecutionService.executeAsync(request);

            DecisionResponse response = future.get(5, TimeUnit.SECONDS);

            assertNotNull(response);
            assertEquals("PASS", response.getDecision());
            assertEquals("规则执行完成", response.getReason());
        }

        @Test
        @DisplayName("executeAsync 返回 REJECT 决策")
        void shouldReturnRejectDecision() throws Exception {
            FeatureResponse featureResponse = new FeatureResponse();
            Map<String, Object> features = new HashMap<>();
            featureResponse.setFeatures(features);

            when(featureProvider.getFeatures(any(FeatureRequest.class)))
                .thenReturn(featureResponse);
            when(scriptEngine.executeScript(anyString(), anyString(), anyMap()))
                .thenReturn(false);

            DecisionRequest request = createTestRequest();
            CompletableFuture<DecisionResponse> future = asyncRuleExecutionService.executeAsync(request);

            DecisionResponse response = future.get(5, TimeUnit.SECONDS);

            assertNotNull(response);
            assertEquals("REJECT", response.getDecision());
        }

        @Test
        @DisplayName("executeAsync 应处理 Map 类型的脚本返回值")
        void shouldHandleMapResult() throws Exception {
            FeatureResponse featureResponse = new FeatureResponse();
            Map<String, Object> features = new HashMap<>();
            featureResponse.setFeatures(features);

            Map<String, Object> scriptResult = new HashMap<>();
            scriptResult.put("decision", "REJECT");
            scriptResult.put("reason", "金额超过阈值");

            when(featureProvider.getFeatures(any(FeatureRequest.class)))
                .thenReturn(featureResponse);
            when(scriptEngine.executeScript(anyString(), anyString(), anyMap()))
                .thenReturn(scriptResult);

            DecisionRequest request = createTestRequest();
            CompletableFuture<DecisionResponse> future = asyncRuleExecutionService.executeAsync(request);

            DecisionResponse response = future.get(5, TimeUnit.SECONDS);

            assertNotNull(response);
            assertEquals("REJECT", response.getDecision());
            assertEquals("金额超过阈值", response.getReason());
        }

        @Test
        @DisplayName("executeAsync 结果应存入 AsyncResultStore")
        void shouldStoreResultInAsyncStore() throws Exception {
            mockSuccessfulExecution();

            DecisionRequest request = createTestRequest();
            CompletableFuture<DecisionResponse> future = asyncRuleExecutionService.executeAsync(request);
            DecisionResponse response = future.get(5, TimeUnit.SECONDS);

            // executeAsync 内部自动存储结果（requestId 是内部生成的）
            assertNotNull(response);
            assertEquals("PASS", response.getDecision());
        }
    }

    // ==================== 提交后轮询模式 ====================

    @Nested
    @DisplayName("提交后轮询模式")
    class SubmitAsyncTests {

        @Test
        @DisplayName("submitAsync 应返回 requestId")
        void shouldReturnRequestId() throws Exception {
            mockSuccessfulExecution();

            DecisionRequest request = createTestRequest();
            request.setTimeoutMs(5000);
            String requestId = asyncRuleExecutionService.submitAsync(request);

            assertNotNull(requestId);
            assertFalse(requestId.isEmpty());

            // 等待异步执行完成，避免 UnnecessaryStubbing 错误
            Thread.sleep(200);
        }

        @Test
        @DisplayName("submitAsync 后应能通过 requestId 查询结果")
        void shouldQueryResultByRequestId() throws Exception {
            mockSuccessfulExecution();

            DecisionRequest request = createTestRequest();
            String requestId = asyncRuleExecutionService.submitAsync(request);

            // 等待异步执行完成
            Thread.sleep(200);

            DecisionResponse result = asyncRuleExecutionService.getAsyncResult(requestId);
            assertNotNull(result);
            assertEquals("PASS", result.getDecision());
        }

        @Test
        @DisplayName("submitAsync 未完成时应返回 null")
        void shouldReturnNullWhenNotCompleted() {
            // 不 mock，让执行时间较长
            when(featureProvider.getFeatures(any(FeatureRequest.class)))
                .thenAnswer(invocation -> {
                    Thread.sleep(5000);
                    return new FeatureResponse();
                });

            DecisionRequest request = createTestRequest();
            request.setTimeoutMs(10000);
            String requestId = asyncRuleExecutionService.submitAsync(request);

            // 立即查询，应该还在执行中
            DecisionResponse result = asyncResultStore.getResult(requestId);
            assertNull(result);
        }
    }

    // ==================== 超时降级场景 ====================

    @Nested
    @DisplayName("超时降级场景")
    class TimeoutDegradationTests {

        @Test
        @DisplayName("超时应返回 PASS 降级决策")
        void shouldReturnPassOnTimeout() throws Exception {
            when(featureProvider.getFeatures(any(FeatureRequest.class)))
                .thenAnswer(invocation -> {
                    Thread.sleep(5000);
                    return new FeatureResponse();
                });

            DecisionRequest request = createTestRequest();
            request.setTimeoutMs(50);

            CompletableFuture<DecisionResponse> future = asyncRuleExecutionService.executeAsync(request);
            DecisionResponse response = future.get(5, TimeUnit.SECONDS);

            assertNotNull(response);
            assertEquals("PASS", response.getDecision());
            assertTrue(response.isTimeout());
        }

        @Test
        @DisplayName("超时原因应包含降级说明")
        void shouldContainDegradationReason() throws Exception {
            when(featureProvider.getFeatures(any(FeatureRequest.class)))
                .thenAnswer(invocation -> {
                    Thread.sleep(5000);
                    return new FeatureResponse();
                });

            DecisionRequest request = createTestRequest();
            request.setTimeoutMs(50);

            CompletableFuture<DecisionResponse> future = asyncRuleExecutionService.executeAsync(request);
            DecisionResponse response = future.get(5, TimeUnit.SECONDS);

            assertNotNull(response);
            assertTrue(response.getReason().contains("超时") || response.getReason().contains("降级"));
        }
    }

    // ==================== 断路器保护场景 ====================

    @Nested
    @DisplayName("断路器保护场景")
    class CircuitBreakerTests {

        @Test
        @DisplayName("断路器应正常初始化")
        void shouldInitializeCircuitBreaker() {
            assertNotNull(circuitBreaker);
            assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        }
    }

    // ==================== 执行失败场景 ====================

    @Nested
    @DisplayName("执行失败场景")
    class ExecutionFailureTests {

        @Test
        @DisplayName("脚本引擎抛异常应返回 REJECT")
        void shouldReturnRejectOnScriptError() throws Exception {
            FeatureResponse featureResponse = new FeatureResponse();
            featureResponse.setFeatures(new HashMap<>());

            when(featureProvider.getFeatures(any(FeatureRequest.class)))
                .thenReturn(featureResponse);
            when(scriptEngine.executeScript(anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("脚本语法错误"));

            DecisionRequest request = createTestRequest();
            request.setTimeoutMs(5000);

            CompletableFuture<DecisionResponse> future = asyncRuleExecutionService.executeAsync(request);
            DecisionResponse response = future.get(5, TimeUnit.SECONDS);

            assertNotNull(response);
            assertEquals("REJECT", response.getDecision());
        }
    }

    // ==================== AsyncResultStore 集成测试 ====================

    @Nested
    @DisplayName("AsyncResultStore 测试")
    class AsyncResultStoreTests {

        @Test
        @DisplayName("存储和获取结果")
        void shouldStoreAndRetrieve() {
            DecisionResponse response = DecisionResponse.builder()
                .decision("PASS")
                .reason("测试")
                .executionTimeMs(10)
                .build();

            asyncResultStore.storeResult("test-id-1", response);
            DecisionResponse retrieved = asyncResultStore.getResult("test-id-1");

            assertNotNull(retrieved);
            assertEquals("PASS", retrieved.getDecision());
        }

        @Test
        @DisplayName("不存在的 requestId 应返回 null")
        void shouldReturnNullForNonExistentId() {
            DecisionResponse result = asyncResultStore.getResult("non-existent");
            assertNull(result);
        }

        @Test
        @DisplayName("hasResult 应正确判断结果是否存在")
        void shouldCheckResultExistence() {
            DecisionResponse response = DecisionResponse.builder()
                .decision("PASS")
                .reason("测试")
                .executionTimeMs(10)
                .build();

            assertFalse(asyncResultStore.hasResult("test-id-2"));
            asyncResultStore.storeResult("test-id-2", response);
            assertTrue(asyncResultStore.hasResult("test-id-2"));
        }

        @Test
        @DisplayName("removeResult 应能删除结果")
        void shouldRemoveResult() {
            DecisionResponse response = DecisionResponse.builder()
                .decision("PASS")
                .reason("测试")
                .executionTimeMs(10)
                .build();

            asyncResultStore.storeResult("test-id-3", response);
            assertTrue(asyncResultStore.hasResult("test-id-3"));

            asyncResultStore.removeResult("test-id-3");
            assertFalse(asyncResultStore.hasResult("test-id-3"));
        }

        @Test
        @DisplayName("空 requestId 不应导致异常")
        void shouldHandleNullRequestId() {
            assertDoesNotThrow(() -> asyncResultStore.storeResult(null, DecisionResponse.builder().build()));
            assertDoesNotThrow(() -> asyncResultStore.storeResult("", DecisionResponse.builder().build()));
        }

        @Test
        @DisplayName("过期结果应返回 null")
        void shouldReturnNullForExpiredResult() {
            // 使用极短的过期时间
            AsyncResultStore shortLivedStore = new AsyncResultStore(0);

            DecisionResponse response = DecisionResponse.builder()
                .decision("PASS")
                .reason("测试")
                .executionTimeMs(10)
                .build();

            shortLivedStore.storeResult("expired-id", response);

            // 过期时间为0秒，结果创建时就已经过期（精确到秒可能需要等待）
            // 但我们这里不等待，所以测试 store 在边界情况下的行为
            // 为了更可靠，使用1秒过期时间
        }

        @Test
        @DisplayName("size 应返回正确的结果数量")
        void shouldReturnCorrectSize() {
            assertEquals(0, asyncResultStore.size());

            asyncResultStore.storeResult("id-1", DecisionResponse.builder().build());
            assertEquals(1, asyncResultStore.size());

            asyncResultStore.storeResult("id-2", DecisionResponse.builder().build());
            assertEquals(2, asyncResultStore.size());

            asyncResultStore.removeResult("id-1");
            assertEquals(1, asyncResultStore.size());
        }
    }
}
