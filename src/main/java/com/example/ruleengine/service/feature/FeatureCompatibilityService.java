package com.example.ruleengine.service.feature;

import com.example.ruleengine.domain.FeatureAlias;
import com.example.ruleengine.domain.FeatureDefinition;
import com.example.ruleengine.repository.FeatureAliasRepository;
import com.example.ruleengine.repository.FeatureDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时特征兼容服务。
 *
 * 目标：在不破坏历史 DSL 的前提下，提供 canonical code 与 alias 的双向兼容。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureCompatibilityService {

    private final FeatureDefinitionRepository featureDefinitionRepository;
    private final FeatureAliasRepository featureAliasRepository;

    private final Map<String, Resolution> resolutionCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> aliasListCache = new ConcurrentHashMap<>();

    /**
     * 扩展输入特征，让 canonical code 与 alias 都可被读取。
     */
    public Map<String, Object> expandFeatures(Map<String, Object> inputFeatures) {
        Map<String, Object> source = inputFeatures != null ? inputFeatures : Map.of();
        Map<String, Object> expanded = new LinkedHashMap<>(source);

        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String rawKey = normalize(entry.getKey());
            if (rawKey == null) {
                continue;
            }
            Object value = entry.getValue();
            Resolution resolution = resolve(rawKey);

            if (resolution.canonicalCode() != null) {
                expanded.putIfAbsent(resolution.canonicalCode(), value);
                for (String alias : aliasesForCanonical(resolution.canonicalCode())) {
                    expanded.putIfAbsent(alias, value);
                }
            } else {
                expanded.putIfAbsent(rawKey, value);
            }
        }

        return expanded;
    }

    /**
     * 将 required feature 列表归一化为 canonical code，避免重复抓取。
     */
    public List<String> normalizeRequiredFeatures(Collection<String> requiredFeatures) {
        if (requiredFeatures == null || requiredFeatures.isEmpty()) {
            return List.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String feature : requiredFeatures) {
            String key = normalize(feature);
            if (key == null) {
                continue;
            }
            Resolution resolution = resolve(key);
            normalized.add(resolution.canonicalCode() != null ? resolution.canonicalCode() : key);
        }
        return new ArrayList<>(normalized);
    }

    /**
     * 根据节点/规则里使用的字段名，从特征 map 中读取兼容值。
     */
    public Object resolveValue(Map<String, Object> features, String requestedKey) {
        if (features == null || features.isEmpty()) {
            return null;
        }
        String key = normalize(requestedKey);
        if (key == null) {
            return null;
        }

        if (features.containsKey(key)) {
            return features.get(key);
        }

        Resolution resolution = resolve(key);
        if (resolution.canonicalCode() != null && features.containsKey(resolution.canonicalCode())) {
            return features.get(resolution.canonicalCode());
        }

        if (resolution.canonicalCode() != null) {
            for (String alias : aliasesForCanonical(resolution.canonicalCode())) {
                if (features.containsKey(alias)) {
                    return features.get(alias);
                }
            }
        }

        return null;
    }

    public String canonicalOrSelf(String featureKey) {
        String key = normalize(featureKey);
        if (key == null) {
            return null;
        }
        Resolution resolution = resolve(key);
        return resolution.canonicalCode() != null ? resolution.canonicalCode() : key;
    }

    private Resolution resolve(String key) {
        return resolutionCache.computeIfAbsent(key, cacheKey -> {
            FeatureDefinition definition = featureDefinitionRepository.findByCodeIgnoreCase(cacheKey).orElse(null);
            if (definition != null) {
                return new Resolution(definition.getCode(), null);
            }

            FeatureAlias alias = featureAliasRepository.findByAliasCodeIgnoreCase(cacheKey).orElse(null);
            if (alias != null) {
                return new Resolution(alias.getCanonicalCode(), alias.getAliasCode());
            }

            return new Resolution(null, null);
        });
    }

    private List<String> aliasesForCanonical(String canonicalCode) {
        if (canonicalCode == null) {
            return List.of();
        }
        return aliasListCache.computeIfAbsent(canonicalCode, code -> featureAliasRepository
                .findByCanonicalCodeOrderByAliasCodeAsc(code)
                .stream()
                .map(FeatureAlias::getAliasCode)
                .filter(Objects::nonNull)
                .toList());
    }

    private String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record Resolution(String canonicalCode, String matchedAlias) {
    }
}
