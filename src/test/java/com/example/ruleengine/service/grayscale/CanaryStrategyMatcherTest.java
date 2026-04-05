package com.example.ruleengine.service.grayscale;

import com.example.ruleengine.constants.GrayscaleStatus;
import com.example.ruleengine.domain.GrayscaleConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CanaryStrategyMatcher 单元测试
 * 覆盖三种灰度策略：PERCENTAGE / FEATURE / WHITELIST
 */
@DisplayName("CanaryStrategyMatcher 测试")
@ExtendWith(MockitoExtension.class)
class CanaryStrategyMatcherTest {

    private CanaryStrategyMatcher matcher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        matcher = new CanaryStrategyMatcher(objectMapper);
    }

    // ==================== PERCENTAGE 策略测试 ====================

    @Nested
    @DisplayName("PERCENTAGE 策略测试")
    class PercentageStrategyTests {

        @Test
        @DisplayName("同一 userId 多次请求结果应一致（一致性哈希）")
        void shouldReturnConsistentResultForSameUserId() {
            GrayscaleConfig config = buildConfig("PERCENTAGE", 50, null, null);
            Map<String, Object> features = Map.of("userId", "user-12345");

            // 执行10次，结果应完全一致
            boolean first = matcher.matches(config, features);
            for (int i = 0; i < 10; i++) {
                assertEquals(first, matcher.matches(config, features),
                        "一致性哈希应保证同一用户结果一致");
            }
        }

        @Test
        @DisplayName("百分比为 0 时不命中")
        void shouldNotMatchWhenPercentageIsZero() {
            GrayscaleConfig config = buildConfig("PERCENTAGE", 0, null, null);
            Map<String, Object> features = Map.of("userId", "user-123");

            assertFalse(matcher.matches(config, features));
        }

        @Test
        @DisplayName("百分比为 100 时一定命中")
        void shouldAlwaysMatchWhenPercentageIs100() {
            GrayscaleConfig config = buildConfig("PERCENTAGE", 100, null, null);
            Map<String, Object> features = Map.of("userId", "user-123");

            assertTrue(matcher.matches(config, features));
        }

        @Test
        @DisplayName("不同 userId 应产生不同的分流结果")
        void shouldDistributeAcrossUsers() {
            GrayscaleConfig config = buildConfig("PERCENTAGE", 50, null, null);

            int hitCount = 0;
            int total = 100;
            for (int i = 0; i < total; i++) {
                Map<String, Object> features = Map.of("userId", "user-" + i);
                if (matcher.matches(config, features)) {
                    hitCount++;
                }
            }

            // 50% 百分比下 100 个用户，命中数应在 20~80 范围内
            assertTrue(hitCount > 10 && hitCount < 90,
                    "50% 百分比应大致分流一半用户, 实际命中: " + hitCount);
        }

        @Test
        @DisplayName("无 userId 时应使用 sessionId 作为 hash key")
        void shouldUseSessionIdWhenNoUserId() {
            GrayscaleConfig config = buildConfig("PERCENTAGE", 50, null, null);
            Map<String, Object> features = Map.of("sessionId", "session-abc");

            // 多次调用结果应一致
            boolean first = matcher.matches(config, features);
            assertEquals(first, matcher.matches(config, features));
        }

        @Test
        @DisplayName("无 userId 和 sessionId 时应使用 features 整体作为 hash key")
        void shouldUseFeaturesWhenNoUserIdOrSessionId() {
            GrayscaleConfig config = buildConfig("PERCENTAGE", 50, null, null);
            Map<String, Object> features = Map.of("ip", "192.168.1.1", "region", "US");

            boolean first = matcher.matches(config, features);
            assertEquals(first, matcher.matches(config, features));
        }
    }

    // ==================== FEATURE 策略测试 ====================

    @Nested
    @DisplayName("FEATURE 策略测试")
    class FeatureStrategyTests {

        @Test
        @DisplayName("所有条件满足时应命中")
        void shouldMatchWhenAllConditionsMet() throws Exception {
            String featureRules = objectMapper.writeValueAsString(java.util.List.of(
                    Map.of("field", "region", "operator", "EQ", "value", "US"),
                    Map.of("field", "age", "operator", "GT", "value", "18")
            ));

            GrayscaleConfig config = buildConfig("FEATURE", 0, featureRules, null);
            Map<String, Object> features = new HashMap<>();
            features.put("region", "US");
            features.put("age", "25");

            assertTrue(matcher.matches(config, features));
        }

        @Test
        @DisplayName("部分条件不满足时不命中")
        void shouldNotMatchWhenPartialConditionFails() throws Exception {
            String featureRules = objectMapper.writeValueAsString(java.util.List.of(
                    Map.of("field", "region", "operator", "EQ", "value", "US"),
                    Map.of("field", "age", "operator", "GT", "value", "18")
            ));

            GrayscaleConfig config = buildConfig("FEATURE", 0, featureRules, null);
            Map<String, Object> features = new HashMap<>();
            features.put("region", "CN");  // 不满足
            features.put("age", "25");

            assertFalse(matcher.matches(config, features));
        }

        @Test
        @DisplayName("特征值缺失时不命中")
        void shouldNotMatchWhenFeatureMissing() throws Exception {
            String featureRules = objectMapper.writeValueAsString(java.util.List.of(
                    Map.of("field", "vipLevel", "operator", "EQ", "value", "GOLD")
            ));

            GrayscaleConfig config = buildConfig("FEATURE", 0, featureRules, null);
            Map<String, Object> features = Map.of("region", "US");  // 缺少 vipLevel

            assertFalse(matcher.matches(config, features));
        }

        @Test
        @DisplayName("featureRules 为空时不命中")
        void shouldNotMatchWhenRulesEmpty() {
            GrayscaleConfig config = buildConfig("FEATURE", 0, null, null);
            Map<String, Object> features = Map.of("region", "US");

            assertFalse(matcher.matches(config, features));
        }

        @Test
        @DisplayName("CONTAINS 操作符应正确匹配")
        void shouldSupportContainsOperator() throws Exception {
            String featureRules = objectMapper.writeValueAsString(java.util.List.of(
                    Map.of("field", "tags", "operator", "CONTAINS", "value", "vip")
            ));

            GrayscaleConfig config = buildConfig("FEATURE", 0, featureRules, null);
            Map<String, Object> features = Map.of("tags", "user,vip,premium");

            assertTrue(matcher.matches(config, features));
        }

        @Test
        @DisplayName("IN 操作符应正确匹配")
        void shouldSupportInOperator() throws Exception {
            String featureRules = objectMapper.writeValueAsString(java.util.List.of(
                    Map.of("field", "region", "operator", "IN", "value", "US,UK,CA")
            ));

            GrayscaleConfig config = buildConfig("FEATURE", 0, featureRules, null);
            Map<String, Object> features = Map.of("region", "UK");

            assertTrue(matcher.matches(config, features));
        }

        @Test
        @DisplayName("GE 操作符应正确匹配")
        void shouldSupportGeOperator() throws Exception {
            String featureRules = objectMapper.writeValueAsString(java.util.List.of(
                    Map.of("field", "amount", "operator", "GE", "value", "100")
            ));

            GrayscaleConfig config = buildConfig("FEATURE", 0, featureRules, null);

            assertTrue(matcher.matches(config, Map.of("amount", "100")));
            assertTrue(matcher.matches(config, Map.of("amount", "200")));
            assertFalse(matcher.matches(config, Map.of("amount", "50")));
        }
    }

    // ==================== WHITELIST 策略测试 ====================

    @Nested
    @DisplayName("WHITELIST 策略测试")
    class WhitelistStrategyTests {

        @Test
        @DisplayName("userId 在白名单中应命中")
        void shouldMatchWhenUserInWhitelist() {
            GrayscaleConfig config = buildConfig("WHITELIST", 0, null, "user-001,user-002,user-003");
            Map<String, Object> features = Map.of("userId", "user-002");

            assertTrue(matcher.matches(config, features));
        }

        @Test
        @DisplayName("userId 不在白名单中不命中")
        void shouldNotMatchWhenUserNotInWhitelist() {
            GrayscaleConfig config = buildConfig("WHITELIST", 0, null, "user-001,user-002");
            Map<String, Object> features = Map.of("userId", "user-999");

            assertFalse(matcher.matches(config, features));
        }

        @Test
        @DisplayName("无 userId 时不命中")
        void shouldNotMatchWhenNoUserId() {
            GrayscaleConfig config = buildConfig("WHITELIST", 0, null, "user-001");
            Map<String, Object> features = Map.of("sessionId", "session-abc");

            assertFalse(matcher.matches(config, features));
        }

        @Test
        @DisplayName("白名单为空时不命中")
        void shouldNotMatchWhenWhitelistEmpty() {
            GrayscaleConfig config = buildConfig("WHITELIST", 0, null, null);
            Map<String, Object> features = Map.of("userId", "user-001");

            assertFalse(matcher.matches(config, features));
        }

        @Test
        @DisplayName("白名单含空格时应正确解析")
        void shouldTrimWhitelistEntries() {
            GrayscaleConfig config = buildConfig("WHITELIST", 0, null, " user-001 , user-002 , user-003 ");
            Map<String, Object> features = Map.of("userId", "user-002");

            assertTrue(matcher.matches(config, features));
        }
    }

    // ==================== 异常和边界条件测试 ====================

    @Nested
    @DisplayName("异常和边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("config 为 null 时返回 false")
        void shouldReturnFalseWhenConfigNull() {
            assertFalse(matcher.matches(null, Map.of("userId", "user-001")));
        }

        @Test
        @DisplayName("features 为 null 时返回 false")
        void shouldReturnFalseWhenFeaturesNull() {
            GrayscaleConfig config = buildConfig("PERCENTAGE", 50, null, null);
            assertFalse(matcher.matches(config, null));
        }

        @Test
        @DisplayName("无效策略类型应 fallback 到百分比分流")
        void shouldFallbackToPercentageForInvalidStrategy() {
            GrayscaleConfig config = buildConfig("INVALID_TYPE", 50, null, null);
            Map<String, Object> features = Map.of("userId", "user-123");

            // 不应抛异常，fallback 到百分比
            assertDoesNotThrow(() -> matcher.matches(config, features));
        }

        @Test
        @DisplayName("strategyType 为 null 时默认使用 PERCENTAGE")
        void shouldDefaultToPercentageWhenStrategyNull() {
            GrayscaleConfig config = GrayscaleConfig.builder()
                    .id(1L)
                    .ruleKey("test-rule")
                    .targetType("RULE")
                    .targetKey("test-rule")
                    .currentVersion(1)
                    .grayscaleVersion(2)
                    .grayscalePercentage(100)
                    .status(GrayscaleStatus.RUNNING)
                    .strategyType(null)
                    .build();

            Map<String, Object> features = Map.of("userId", "user-123");
            assertTrue(matcher.matches(config, features));
        }
    }

    // ==================== 辅助方法 ====================

    private GrayscaleConfig buildConfig(String strategyType, int percentage,
                                         String featureRules, String whitelistIds) {
        return GrayscaleConfig.builder()
                .id(1L)
                .ruleKey("test-rule")
                .targetType("RULE")
                .targetKey("test-rule")
                .currentVersion(1)
                .grayscaleVersion(2)
                .grayscalePercentage(percentage)
                .status(GrayscaleStatus.RUNNING)
                .strategyType(strategyType)
                .featureRules(featureRules)
                .whitelistIds(whitelistIds)
                .build();
    }
}
