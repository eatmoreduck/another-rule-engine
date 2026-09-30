package com.example.ruleengine.service;

import com.example.ruleengine.domain.FeatureAlias;
import com.example.ruleengine.domain.FeatureDefinition;
import com.example.ruleengine.model.FeatureRequest;
import com.example.ruleengine.model.FeatureResponse;
import com.example.ruleengine.repository.FeatureAliasRepository;
import com.example.ruleengine.repository.FeatureDefinitionRepository;
import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
 * 4. Phase 9: canonical code + alias fallback 兼容
 */
@Service
public class FeatureProviderService {

    private static final Logger logger = LoggerFactory.getLogger(FeatureProviderService.class);

    private final Cache<String, Object> featureCache;
    private final RestTemplate restTemplate;
    private final Map<String, Object> defaultFeatures;
    private final FeatureAliasRepository featureAliasRepository;
    private final FeatureDefinitionRepository featureDefinitionRepository;
    private final Map<String, String> resolvedCodeCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> aliasListCache = new ConcurrentHashMap<>();

    public FeatureProviderService(
        @Qualifier("featureCache") Cache<String, Object> featureCache,
        RestTemplate restTemplate
    ) {
        this(featureCache, restTemplate, null, null);
    }

    @Autowired
    public FeatureProviderService(
        @Qualifier("featureCache") Cache<String, Object> featureCache,
        RestTemplate restTemplate,
        @Nullable FeatureAliasRepository featureAliasRepository,
        @Nullable FeatureDefinitionRepository featureDefinitionRepository
    ) {
        this.featureCache = featureCache;
        this.restTemplate = restTemplate;
        this.featureAliasRepository = featureAliasRepository;
        this.featureDefinitionRepository = featureDefinitionRepository;
        this.defaultFeatures = loadDefaultFeatures();
    }

    /**
     * 获取特征（三级策略）
     * D-14: 入参优先 → 外部降级 → 默认值
     */
    public FeatureResponse getFeatures(FeatureRequest request) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> inputFeatures = request.getInputFeatures() != null
            ? new HashMap<>(request.getInputFeatures())
            : new HashMap<>();
        Map<String, Object> result = new HashMap<>(inputFeatures);
        boolean fallbackToDefault = false;

        mirrorKnownAliases(inputFeatures, result);

        List<String> requiredFeatures = request.getRequiredFeatures() != null
            ? request.getRequiredFeatures().stream().map(this::normalizeKey).filter(key -> !key.isEmpty()).toList()
            : Collections.emptyList();

        Map<String, String> requestedToCanonical = new LinkedHashMap<>();
        List<String> canonicalMissingFeatures = new ArrayList<>();
        for (String requested : requiredFeatures) {
            if (result.containsKey(requested)) {
                continue;
            }
            String canonical = resolveCanonicalCode(requested);
            requestedToCanonical.put(requested, canonical);
            if (!result.containsKey(canonical)) {
                canonicalMissingFeatures.add(canonical);
            }
        }

        List<String> uniqueCanonicalMissing = canonicalMissingFeatures.stream()
            .distinct()
            .toList();

        if (!uniqueCanonicalMissing.isEmpty()) {
            Map<String, Object> cachedFeatures = getFeaturesFromCache(uniqueCanonicalMissing);
            cachedFeatures.forEach((key, value) -> putResolvedFeatureValue(result, key, value, false));

            List<String> stillMissing = uniqueCanonicalMissing.stream()
                .filter(feature -> !result.containsKey(feature))
                .toList();

            if (!stillMissing.isEmpty()) {
                Map<String, Object> externalFeatures = fetchExternalFeaturesWithTimeout(
                    stillMissing,
                    request.getTimeoutMs()
                );
                externalFeatures.forEach((key, value) -> putResolvedFeatureValue(result, key, value, true));

                List<String> finalMissing = stillMissing.stream()
                    .filter(feature -> !result.containsKey(feature))
                    .toList();

                if (!finalMissing.isEmpty()) {
                    finalMissing.forEach(feature -> {
                        if (defaultFeatures.containsKey(feature)) {
                            putResolvedFeatureValue(result, feature, defaultFeatures.get(feature), true);
                        } else {
                            result.putIfAbsent(feature, null);
                        }
                    });
                    fallbackToDefault = true;
                    logger.debug("Used default values for features: {}", finalMissing);
                }
            }
        }

        requestedToCanonical.forEach((requested, canonical) -> {
            if (!result.containsKey(requested) && result.containsKey(canonical)) {
                result.put(requested, result.get(canonical));
            }
        });

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

            Map<String, Object> result = externalFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
            return result != null ? result : Collections.emptyMap();

        } catch (Exception e) {
            logger.warn("Failed to fetch external features: {}", featureKeys, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 调用外部特征平台
     */
    private Map<String, Object> fetchExternalFeatures(List<String> featureKeys) {
        try {
            Map<String, Object> response = restTemplate.postForObject(
                "http://feature-platform/api/features",
                featureKeys,
                Map.class
            );
            return response != null ? response : Collections.emptyMap();
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
            features.forEach((key, value) -> putResolvedFeatureValue(new HashMap<>(), key, value, true));
            logger.info("Preloaded {} features", features.size());
        });
    }

    /**
     * PERF-02: 批量获取特征
     */
    public Map<String, Object> batchGetFeatures(List<String> featureKeys, long timeoutMs) {
        FeatureRequest request = new FeatureRequest(Collections.emptyMap(), featureKeys);
        request.setTimeoutMs(timeoutMs);
        return getFeatures(request).getFeatures();
    }

    private void mirrorKnownAliases(Map<String, Object> inputFeatures, Map<String, Object> result) {
        inputFeatures.forEach((key, value) -> putResolvedFeatureValue(result, key, value, false));
    }

    private void putResolvedFeatureValue(Map<String, Object> result, String featureCode, Object value, boolean populateCache) {
        String normalizedCode = normalizeKey(featureCode);
        if (normalizedCode.isEmpty()) {
            return;
        }
        String canonical = resolveCanonicalCode(normalizedCode);
        result.put(normalizedCode, value);
        result.put(canonical, value);

        if (populateCache && value != null) {
            featureCache.put(canonical, value);
            featureCache.put(normalizedCode, value);
        }

        for (String alias : listAliasesForCanonical(canonical)) {
            result.putIfAbsent(alias, value);
            if (populateCache && value != null) {
                featureCache.put(alias, value);
            }
        }
    }

    private String resolveCanonicalCode(String featureCode) {
        String normalizedCode = normalizeKey(featureCode);
        if (normalizedCode.isEmpty()) {
            return normalizedCode;
        }
        return resolvedCodeCache.computeIfAbsent(normalizedCode, key -> {
            if (featureDefinitionRepository != null) {
                Optional<FeatureDefinition> definition = featureDefinitionRepository.findByCodeIgnoreCase(key);
                if (definition.isPresent()) {
                    listAliasesForCanonical(definition.get().getCode());
                    return definition.get().getCode();
                }
            }
            if (featureAliasRepository != null) {
                Optional<FeatureAlias> alias = featureAliasRepository.findByAliasCodeIgnoreCase(key);
                if (alias.isPresent()) {
                    listAliasesForCanonical(alias.get().getCanonicalCode());
                    return alias.get().getCanonicalCode();
                }
            }
            return key;
        });
    }

    private List<String> listAliasesForCanonical(String canonicalCode) {
        String normalizedCode = normalizeKey(canonicalCode);
        if (normalizedCode.isEmpty() || featureAliasRepository == null) {
            return List.of();
        }
        return aliasListCache.computeIfAbsent(normalizedCode, key -> featureAliasRepository
            .findByCanonicalCodeOrderByAliasCodeAsc(key)
            .stream()
            .map(FeatureAlias::getAliasCode)
            .map(this::normalizeKey)
            .filter(alias -> !alias.equals(key))
            .collect(Collectors.toList()));
    }

    private String normalizeKey(String featureCode) {
        return featureCode == null ? "" : featureCode.trim();
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
        return defaults;
    }
}
