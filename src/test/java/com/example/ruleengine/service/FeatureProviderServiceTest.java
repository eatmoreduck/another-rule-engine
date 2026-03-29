package com.example.ruleengine.service;

import com.example.ruleengine.model.FeatureRequest;
import com.example.ruleengine.model.FeatureResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * FeatureProviderService 单元测试
 * <p>
 * 测试覆盖:
 * 1. 三级策略（入参 -> 外部 -> 默认值）
 * 2. 缓存命中/未命中
 * 3. 超时降级
 * 4. 边界条件
 */
@DisplayName("FeatureProviderService 测试")
@ExtendWith(MockitoExtension.class)
class FeatureProviderServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private Cache<String, Object> featureCache;
    private FeatureProviderService featureProvider;

    @BeforeEach
    void setUp() {
        featureCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();
        featureProvider = new FeatureProviderService(featureCache, restTemplate);
    }

    // ==================== 三级策略测试 ====================

    @Nested
    @DisplayName("三级策略测试")
    class ThreeLevelStrategyTests {

        @Test
        @DisplayName("第一级: 入参已包含所有特征时，直接返回")
        void shouldReturnInputFeaturesWhenAllPresent() {
            // Arrange
            Map<String, Object> inputFeatures = new HashMap<>();
            inputFeatures.put("user_age", 25);
            inputFeatures.put("user_level", "VIP");

            FeatureRequest request = new FeatureRequest(
                inputFeatures,
                Arrays.asList("user_age", "user_level")
            );

            // Act
            FeatureResponse response = featureProvider.getFeatures(request);

            // Assert
            assertNotNull(response);
            assertEquals(2, response.getFeatures().size());
            assertEquals(25, response.getFeatures().get("user_age"));
            assertEquals("VIP", response.getFeatures().get("user_level"));
            assertFalse(response.isFallbackToDefault());

            // 验证没有调用外部服务
            verify(restTemplate, never()).postForObject(any(), any(), any());
        }

        @Test
        @DisplayName("第三级: 外部获取失败时，使用默认值")
        void shouldFallbackToDefaultWhenExternalFails() {
            // Arrange
            Map<String, Object> inputFeatures = new HashMap<>();

            // 模拟外部服务返回空
            when(restTemplate.postForObject(any(), any(), eq(Map.class)))
                .thenReturn(Collections.emptyMap());

            FeatureRequest request = new FeatureRequest(
                inputFeatures,
                Arrays.asList("user_age", "user_level")
            );
            request.setTimeoutMs(1000);

            // Act
            FeatureResponse response = featureProvider.getFeatures(request);

            // Assert
            assertTrue(response.isFallbackToDefault());
            // 验证使用了默认值
            assertEquals(0, response.getFeatures().get("user_age")); // 默认值
            assertEquals("NORMAL", response.getFeatures().get("user_level")); // 默认值
        }

        @Test
        @DisplayName("第三级: 外部服务抛出异常时，使用默认值")
        void shouldFallbackToDefaultWhenExternalThrowsException() {
            // Arrange
            Map<String, Object> inputFeatures = new HashMap<>();

            when(restTemplate.postForObject(any(), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

            FeatureRequest request = new FeatureRequest(
                inputFeatures,
                Arrays.asList("risk_score")
            );
            request.setTimeoutMs(1000);

            // Act
            FeatureResponse response = featureProvider.getFeatures(request);

            // Assert
            assertTrue(response.isFallbackToDefault());
            assertEquals(0.5, response.getFeatures().get("risk_score")); // 默认值
        }
    }

    // ==================== 缓存测试 ====================

    @Nested
    @DisplayName("缓存测试")
    class CacheTests {

        @Test
        @DisplayName("缓存命中: 从缓存获取特征")
        void shouldGetFeatureFromCache() {
                // Arrange - 预先放入缓存
                featureCache.put("user_level", "VIP");

                Map<String, Object> inputFeatures = new HashMap<>();
                FeatureRequest request = new FeatureRequest(
                        inputFeatures,
                        Arrays.asList("user_level")
                );
                request.setTimeoutMs(1000);

                // Act
                FeatureResponse response = featureProvider.getFeatures(request);

                // Assert
                assertEquals("VIP", response.getFeatures().get("user_level"));
                // 缓存命中时不应该调用外部服务
                verify(restTemplate, never()).postForObject(any(), any(), any());
        }
    }

    // ==================== 超时降级测试 ====================

    @Nested
    @DisplayName("超时降级测试")
    class TimeoutTests {

        @Test
        @DisplayName("超时时使用默认值")
        void shouldFallbackToDefaultOnTimeout() {
            // Arrange - 模拟超时场景
            when(restTemplate.postForObject(any(), any(), eq(Map.class)))
                .thenAnswer(invocation -> {
                    Thread.sleep(200); // 模拟超时
                    return Collections.emptyMap();
                });

            Map<String, Object> inputFeatures = new HashMap<>();
            FeatureRequest request = new FeatureRequest(
                inputFeatures,
                Arrays.asList("order_amount")
            );
            request.setTimeoutMs(50); // 设置 50ms 超时

            // Act
            FeatureResponse response = featureProvider.getFeatures(request);

            // Assert - 超时后应使用默认值
            assertTrue(response.isFallbackToDefault());
            assertEquals(0.0, response.getFeatures().get("order_amount")); // 默认值
        }
    }

    // ==================== 边界条件测试 ====================

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("不需要额外特征时直接返回")
        void shouldReturnDirectlyWhenNoRequiredFeatures() {
            // Arrange
            Map<String, Object> inputFeatures = new HashMap<>();
            inputFeatures.put("user_age", 25);

            FeatureRequest request = new FeatureRequest(
                inputFeatures,
                Collections.emptyList()
            );

            // Act
            FeatureResponse response = featureProvider.getFeatures(request);

            // Assert
            assertEquals(1, response.getFeatures().size());
            assertFalse(response.isFallbackToDefault());
            verify(restTemplate, never()).postForObject(any(), any(), any());
        }

        @Test
        @DisplayName("外部服务返回 null 时使用默认值")
        void shouldFallbackWhenExternalReturnsNull() {
            // Arrange
            when(restTemplate.postForObject(any(), any(), eq(Map.class)))
                .thenReturn(null);

            FeatureRequest request = new FeatureRequest(
                Collections.emptyMap(),
                Arrays.asList("user_age")
            );
            request.setTimeoutMs(1000);

            // Act
            FeatureResponse response = featureProvider.getFeatures(request);

            // Assert
            assertTrue(response.isFallbackToDefault());
            // 验证使用了默认值
            assertEquals(0, response.getFeatures().get("user_age")); // 默认值
        }

    }

    // ==================== 批量操作测试 ====================
    @Nested
    @DisplayName("批量操作测试")
    class BatchOperationTests {

        @Test
        @DisplayName("批量操作 - 通过缓存获取多个特征")
        void shouldGetMultipleFeaturesFromCache() {
            // Arrange - 手动将多个特征放入缓存
            featureCache.put("batch_f1", 100);
            featureCache.put("batch_f2", "cached_value");

            Map<String, Object> inputFeatures = new HashMap<>();
            FeatureRequest request = new FeatureRequest(
                inputFeatures,
                Arrays.asList("batch_f1", "batch_f2")
            );

            // Act
            FeatureResponse response = featureProvider.getFeatures(request);

            // Assert - 所有特征都从缓存获取，不调用外部服务
            assertNotNull(response);
            assertEquals(2, response.getFeatures().size());
            assertEquals(100, response.getFeatures().get("batch_f1"));
            assertEquals("cached_value", response.getFeatures().get("batch_f2"));
            assertFalse(response.isFallbackToDefault());
            verify(restTemplate, never()).postForObject(any(), any(), any());
        }
    }

    // ==================== 性能相关测试 ====================

    @Nested
    @DisplayName("性能相关测试")
    class PerformanceTests {

        @Test
        @DisplayName("响应时间应记录")
        void shouldRecordFetchTime() {
            // Arrange
            FeatureRequest request = new FeatureRequest(
                new HashMap<>(),
                Arrays.asList("user_age")
            );
            request.setTimeoutMs(1000);

            when(restTemplate.postForObject(any(), any(), eq(Map.class)))
                .thenReturn(Collections.singletonMap("user_age", 30));

            // Act
            FeatureResponse response = featureProvider.getFeatures(request);

            // Assert
            assertTrue(response.getFetchTimeMs() >= 0);
        }
    }
}
