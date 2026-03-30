package com.example.ruleengine.service.degradation;

import com.example.ruleengine.model.DecisionRequest;
import com.example.ruleengine.model.DecisionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DegradationService 单元测试
 * 覆盖场景：超时降级、熔断降级、响应字段校验
 */
class DegradationServiceTest {

    private DegradationService degradationService;
    private DecisionRequest request;

    @BeforeEach
    void setUp() {
        degradationService = new DegradationService();
        Map<String, Object> features = new HashMap<>();
        features.put("amount", 100.0);
        request = new DecisionRequest("rule-001", "return PASS", features);
    }

    @Test
    @DisplayName("超时降级应返回 PASS 决策和超时原因")
    void timeoutFallbackShouldReturnPassWithTimeoutReason() {
        Throwable timeoutException = new TimeoutException("执行超时");

        DecisionResponse response = degradationService.getTimeoutFallback(request, timeoutException);

        assertEquals("PASS", response.getDecision());
        assertEquals("规则执行超时，降级返回PASS", response.getReason());
    }

    @Test
    @DisplayName("超时降级响应应包含 executionTimeMs=50")
    void timeoutFallbackShouldContainExecutionTimeMs() {
        Throwable timeoutException = new TimeoutException("执行超时");

        DecisionResponse response = degradationService.getTimeoutFallback(request, timeoutException);

        assertEquals(50, response.getExecutionTimeMs());
    }

    @Test
    @DisplayName("熔断降级应返回 PASS 决策和熔断原因")
    void circuitBreakerFallbackShouldReturnPassWithCircuitBreakerReason() {
        Throwable circuitBreakerException = new RuntimeException("Circuit breaker open");

        DecisionResponse response = degradationService.getCircuitBreakerFallback(request, circuitBreakerException);

        assertEquals("PASS", response.getDecision());
        assertEquals("熔断器开启，降级返回PASS", response.getReason());
    }

    @Test
    @DisplayName("熔断降级响应应包含 executionTimeMs=0")
    void circuitBreakerFallbackShouldContainExecutionTimeMsZero() {
        Throwable circuitBreakerException = new RuntimeException("Circuit breaker open");

        DecisionResponse response = degradationService.getCircuitBreakerFallback(request, circuitBreakerException);

        assertEquals(0, response.getExecutionTimeMs());
    }

    @Test
    @DisplayName("降级响应对象不应为 null")
    void fallbackResponsesShouldNotBeNull() {
        DecisionResponse timeoutResponse = degradationService.getTimeoutFallback(request, new TimeoutException());
        DecisionResponse circuitBreakerResponse = degradationService.getCircuitBreakerFallback(request, new RuntimeException());

        assertNotNull(timeoutResponse);
        assertNotNull(circuitBreakerResponse);
    }
}
