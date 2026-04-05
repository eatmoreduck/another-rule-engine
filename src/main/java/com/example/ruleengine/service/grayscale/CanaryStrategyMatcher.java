package com.example.ruleengine.service.grayscale;

import com.example.ruleengine.constants.CanaryStrategyType;
import com.example.ruleengine.domain.GrayscaleConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 灰度策略匹配器
 * 根据灰度配置的策略类型判断请求是否命中灰度
 *
 * 支持三种策略：
 * 1. PERCENTAGE - 一致性哈希百分比分流（基于 userId 或 sessionId）
 * 2. FEATURE - 特征匹配（基于请求特征条件 JSON）
 * 3. WHITELIST - 用户白名单（精确匹配用户ID）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CanaryStrategyMatcher {

    private final ObjectMapper objectMapper;

    /**
     * 判断请求是否命中灰度配置
     *
     * @param config   灰度配置
     * @param features 请求特征数据（包含 userId, sessionId 等）
     * @return true 表示命中灰度，应使用灰度版本
     */
    public boolean matches(GrayscaleConfig config, Map<String, Object> features) {
        if (config == null || features == null) {
            return false;
        }

        try {
            String strategyType = config.getStrategyType();
            if (strategyType == null) {
                strategyType = "PERCENTAGE";
            }

            switch (CanaryStrategyType.valueOf(strategyType)) {
                case PERCENTAGE:
                    return matchByPercentage(config, features);
                case FEATURE:
                    return matchByFeature(config, features);
                case WHITELIST:
                    return matchByWhitelist(config, features);
                default:
                    log.warn("未知的灰度策略类型: {}, fallback 到百分比分流", strategyType);
                    return matchByPercentage(config, features);
            }
        } catch (IllegalArgumentException e) {
            log.warn("无效的灰度策略类型: {}, fallback 到百分比分流", config.getStrategyType());
            return matchByPercentage(config, features);
        } catch (Exception e) {
            log.error("灰度策略匹配异常, fallback 到不命中: configId={}", config.getId(), e);
            return false;
        }
    }

    /**
     * 一致性哈希百分比分流
     * 基于 userId 或 sessionId 的 hashCode % 100 < percentage
     * 同一用户多次请求结果一致
     */
    private boolean matchByPercentage(GrayscaleConfig config, Map<String, Object> features) {
        int percentage = config.getGrayscalePercentage() != null ? config.getGrayscalePercentage() : 0;
        if (percentage <= 0) {
            return false;
        }
        if (percentage >= 100) {
            return true;
        }

        // 优先使用 userId，其次 sessionId，最后使用整个 features 的 hashCode
        String hashKey = extractHashKey(features);
        int hashValue = Math.abs(hashKey.hashCode() % 100);
        boolean matched = hashValue < percentage;

        log.debug("百分比分流: hashKey={}, hashValue={}, percentage={}, matched={}",
                hashKey, hashValue, percentage, matched);
        return matched;
    }

    /**
     * 特征匹配
     * 从 config.featureRules 解析 JSON 条件列表，逐一匹配
     * featureRules 格式: [{"field":"region","operator":"EQ","value":"US"},{"field":"age","operator":"GT","value":"18"}]
     * 所有条件需全部满足（AND 语义）
     */
    private boolean matchByFeature(GrayscaleConfig config, Map<String, Object> features) {
        String featureRulesJson = config.getFeatureRules();
        if (featureRulesJson == null || featureRulesJson.isBlank()) {
            log.debug("特征匹配: featureRules 为空, 不命中");
            return false;
        }

        try {
            List<Map<String, Object>> rules = objectMapper.readValue(
                    featureRulesJson, new TypeReference<List<Map<String, Object>>>() {});

            if (rules.isEmpty()) {
                return false;
            }

            for (Map<String, Object> rule : rules) {
                String field = rule.get("field") != null ? rule.get("field").toString() : "";
                String operator = rule.get("operator") != null ? rule.get("operator").toString() : "EQ";
                Object expectedValue = rule.get("value");

                Object actualValue = features.get(field);
                if (!evaluateCondition(actualValue, operator, expectedValue)) {
                    log.debug("特征匹配: 条件不满足 field={}, expected={}, actual={}",
                            field, expectedValue, actualValue);
                    return false;
                }
            }

            log.debug("特征匹配: 所有条件满足");
            return true;
        } catch (Exception e) {
            log.error("特征匹配解析失败: configId={}", config.getId(), e);
            return false;
        }
    }

    /**
     * 用户白名单匹配
     * 从 config.whitelistIds 解析逗号分隔的用户ID列表，精确匹配 features 中的 userId
     */
    private boolean matchByWhitelist(GrayscaleConfig config, Map<String, Object> features) {
        String whitelistIds = config.getWhitelistIds();
        if (whitelistIds == null || whitelistIds.isBlank()) {
            log.debug("白名单匹配: whitelistIds 为空, 不命中");
            return false;
        }

        Object userIdObj = features.get("userId");
        if (userIdObj == null) {
            log.debug("白名单匹配: features 中无 userId, 不命中");
            return false;
        }

        String userId = userIdObj.toString();
        List<String> whitelist = parseWhitelist(whitelistIds);
        boolean matched = whitelist.contains(userId);

        log.debug("白名单匹配: userId={}, matched={}", userId, matched);
        return matched;
    }

    /**
     * 提取一致性哈希的 key
     * 优先级：userId > sessionId > 所有 features 拼接
     */
    private String extractHashKey(Map<String, Object> features) {
        Object userId = features.get("userId");
        if (userId != null && !userId.toString().isEmpty()) {
            return userId.toString();
        }

        Object sessionId = features.get("sessionId");
        if (sessionId != null && !sessionId.toString().isEmpty()) {
            return sessionId.toString();
        }

        // 兜底：使用所有 features 的拼接作为 hash key
        return features.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    /**
     * 解析白名单字符串为列表
     */
    private List<String> parseWhitelist(String whitelistIds) {
        if (whitelistIds == null || whitelistIds.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(whitelistIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 评估单个条件
     */
    private boolean evaluateCondition(Object actualValue, String operator, Object expectedValue) {
        if (actualValue == null) {
            return false;
        }

        try {
            String actual = actualValue.toString();
            String expected = expectedValue != null ? expectedValue.toString() : "";

            switch (operator) {
                case "EQ":
                    return actual.equals(expected);
                case "NE":
                    return !actual.equals(expected);
                case "CONTAINS":
                    return actual.contains(expected);
                case "NOT_CONTAINS":
                    return !actual.contains(expected);
                case "GT":
                    return Double.parseDouble(actual) > Double.parseDouble(expected);
                case "GE":
                    return Double.parseDouble(actual) >= Double.parseDouble(expected);
                case "LT":
                    return Double.parseDouble(actual) < Double.parseDouble(expected);
                case "LE":
                    return Double.parseDouble(actual) <= Double.parseDouble(expected);
                case "IN":
                    List<String> inList = Arrays.stream(expected.split(","))
                            .map(String::trim)
                            .collect(Collectors.toList());
                    return inList.contains(actual);
                default:
                    log.warn("未知的条件操作符: {}", operator);
                    return false;
            }
        } catch (NumberFormatException e) {
            log.debug("数值比较失败: actualValue={}, expectedValue={}", actualValue, expectedValue);
            return false;
        }
    }
}
