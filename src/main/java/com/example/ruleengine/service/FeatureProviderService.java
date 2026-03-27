package com.example.ruleengine.service;

import com.example.ruleengine.model.FeatureRequest;
import com.example.ruleengine.model.FeatureResponse;
import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 特征提供者服务
 * Source: RESEARCH.md 模式4 + CONTEXT.md 决策 D-14, D-15
 *
 * 功能：
 * 1. D-14: 三级策略（入参 → 外部 → 默认值）
 * 2. D-15: 超时控制和降级
 * 3. PERF-02: 特征预加载和批量获取
 */
@Service
public class FeatureProviderService {

    private static final Logger logger = LoggerFactory.getLogger(FeatureProviderService.class);

    private final Cache<String, Object> featureCache;
    private final RestTemplate restTemplate;
    private final Map<String, Object> defaultFeatures;

    public FeatureProviderService(
        @Qualifier("featureCache") Cache<String, Object> featureCache,
        RestTemplate restTemplate
    ) {
        this.featureCache = featureCache;
        this.restTemplate = restTemplate;
        this.defaultFeatures = loadDefaultFeatures();
    }

    /**
     * 获取特征（三级策略）
     * D-14: 入参优先 → 外部降级 → 默认值
     */
    public FeatureResponse getFeatures(FeatureRequest request) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>(request.getInputFeatures());
        boolean fallbackToDefault = false;

        // 找出缺失的特征
        List<String> missingFeatures = request.getRequiredFeatures().stream()
            .filter(feature -> !request.getInputFeatures().containsKey(feature))
            .collect(Collectors.toList());

        if (!missingFeatures.isEmpty()) {
            // 从缓存获取
            Map<String, Object> cachedFeatures = getFeaturesFromCache(missingFeatures);
            result.putAll(cachedFeatures);

            // 仍然缺失的特征
            List<String> stillMissing = missingFeatures.stream()
                .filter(feature -> !cachedFeatures.containsKey(feature))
                .collect(Collectors.toList());

            if (!stillMissing.isEmpty()) {
                // D-15: 从外部特征平台获取（带超时控制）
                Map<String, Object> externalFeatures = fetchExternalFeaturesWithTimeout(
                    stillMissing,
                    request.getTimeoutMs()
                );
                result.putAll(externalFeatures);

                // 仍然缺失的特征，使用默认值
                List<String> finalMissing = stillMissing.stream()
                    .filter(feature -> !externalFeatures.containsKey(feature))
                    .collect(Collectors.toList());

                if (!finalMissing.isEmpty()) {
                    finalMissing.forEach(feature ->
                        result.put(feature, defaultFeatures.getOrDefault(feature, null))
                    );
                    fallbackToDefault = true;
                    logger.debug("Used default values for features: {}", finalMissing);
                }
            }
        }

        long fetchTime = System.currentTimeMillis() - startTime;

        FeatureResponse response = new FeatureResponse();
        response.setFeatures(result);
        response.setCacheHit(false);  // TODO: 实现缓存命中检测
        response.setFallbackToDefault(fallbackToDefault);
        response.setFetchTimeMs(fetchTime);

        return response;
    }

    /**
     * 从缓存获取特征
     */
    private Map<String, Object> getFeaturesFromCache(List<String> featureKeys) {
        Map<String, Object> result = new HashMap<>();
        for (String key : featureKeys) {
            Object value = featureCache.getIfPresent(key);
            if (value != null) {
                result.put(key, value);
                logger.debug("Cache hit for feature: {}", key);
            }
        }
        return result;
    }

    /**
     * 从外部特征平台获取特征（带超时控制）
     * D-15: 使用 CompletableFuture.anyOf() 实现超时控制
     */
    private Map<String, Object> fetchExternalFeaturesWithTimeout(
        List<String> featureKeys,
        long timeoutMs
    ) {
        try {
            CompletableFuture<Map<String, Object>> externalFuture =
                CompletableFuture.supplyAsync(() -> fetchExternalFeatures(featureKeys));

            // 超时控制
            Map<String, Object> result = externalFuture.get(timeoutMs, TimeUnit.MILLISECONDS);

            // 将获取到的特征放入缓存
            result.forEach((key, value) -> featureCache.put(key, value));

            return result;

        } catch (Exception e) {
            logger.warn("Failed to fetch external features: {}", featureKeys, e);
            return Collections.emptyMap();  // 失败返回空，触发默认值降级
        }
    }

    /**
     * 调用外部特征平台
     */
    private Map<String, Object> fetchExternalFeatures(List<String> featureKeys) {
        try {
            // TODO: 实现实际的 HTTP 调用
            // 当前返回模拟数据
            return restTemplate.postForObject(
                "http://feature-platform/api/features",
                featureKeys,
                Map.class
            );
        } catch (Exception e) {
            logger.error("Failed to call external feature platform", e);
            return Collections.emptyMap();
        }
    }

    /**
     * PERF-02: 预加载特征到缓存
     */
    public void preloadFeatures(List<String> featureKeys) {
        logger.info("Preloading features: {}", featureKeys);

        CompletableFuture.runAsync(() -> {
            Map<String, Object> features = fetchExternalFeatures(featureKeys);
            features.forEach((key, value) -> featureCache.put(key, value));
            logger.info("Preloaded {} features", features.size());
        });
    }

    /**
     * PERF-02: 批量获取特征
     */
    public Map<String, Object> batchGetFeatures(List<String> featureKeys, long timeoutMs) {
        return fetchExternalFeaturesWithTimeout(featureKeys, timeoutMs);
    }

    /**
     * 加载默认特征值
     */
    private Map<String, Object> loadDefaultFeatures() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("user_age", 0);
        defaults.put("user_level", "NORMAL");
        defaults.put("order_amount", 0.0);
        defaults.put("risk_score", 0.5);
        // TODO: 从配置文件读取默认值
        return defaults;
    }
}
