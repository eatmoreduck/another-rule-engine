package com.example.ruleengine.cache;

import com.example.ruleengine.constants.GrayscaleStatus;
import com.example.ruleengine.domain.GrayscaleConfig;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.domain.RuleVersion;
import com.example.ruleengine.repository.GrayscaleConfigRepository;
import com.example.ruleengine.repository.RuleRepository;
import com.example.ruleengine.repository.RuleVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 规则缓存服务
 * 支持规则主数据缓存、版本维度缓存和灰度配置缓存
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleCacheService {

    private final RuleRepository ruleRepository;
    private final RuleVersionRepository ruleVersionRepository;
    private final GrayscaleConfigRepository grayscaleConfigRepository;

    /**
     * 从缓存获取规则
     */
    @Cacheable(value = "rules", key = "#ruleKey")
    public Rule getRule(String ruleKey) {
        log.debug("缓存未命中，从数据库加载规则: ruleKey={}", ruleKey);
        return ruleRepository.findByRuleKey(ruleKey).orElse(null);
    }

    /**
     * 从缓存获取启用的规则
     */
    @Cacheable(value = "rules", key = "'enabled:' + #ruleKey")
    public Rule getEnabledRule(String ruleKey) {
        log.debug("缓存未命中，从数据库加载启用规则: ruleKey={}", ruleKey);
        return ruleRepository.findByRuleKeyAndEnabledTrue(ruleKey).orElse(null);
    }

    /**
     * 从缓存获取指定版本的规则脚本
     * 缓存 key: rule-version:{ruleKey}:{version}，TTL 5 分钟
     *
     * @param ruleKey 规则Key
     * @param version 版本号
     * @return 规则版本，不存在返回 null
     */
    @Cacheable(value = "rule-versions", key = "#ruleKey + ':' + #version")
    public RuleVersion getRuleVersion(String ruleKey, Integer version) {
        log.debug("缓存未命中，从数据库加载规则版本: ruleKey={}, version={}", ruleKey, version);
        return ruleVersionRepository.findByRuleKeyAndVersion(ruleKey, version).orElse(null);
    }

    /**
     * 从缓存获取灰度配置（运行中）
     * 缓存 key: grayscale-config:{targetType}:{targetKey}，TTL 10 秒
     * 灰度策略可能随时变更，使用短 TTL 保证时效性
     *
     * @param targetType 目标类型（RULE / DECISION_FLOW）
     * @param targetKey  目标Key
     * @return 灰度配置，不存在返回 null
     */
    @Cacheable(value = "grayscale-configs", key = "#targetType + ':' + #targetKey")
    public GrayscaleConfig getActiveGrayscaleConfig(String targetType, String targetKey) {
        log.debug("缓存未命中，从数据库加载灰度配置: targetType={}, targetKey={}", targetType, targetKey);
        return grayscaleConfigRepository
                .findByTargetTypeAndTargetKeyAndStatus(targetType, targetKey, GrayscaleStatus.RUNNING)
                .orElse(null);
    }

    /**
     * 清除指定规则缓存
     */
    @CacheEvict(value = "rules", key = "#ruleKey")
    public void evictRule(String ruleKey) {
        log.info("清除规则缓存: ruleKey={}", ruleKey);
    }

    /**
     * 清除指定规则版本缓存
     *
     * @param ruleKey 规则Key
     * @param version 版本号
     */
    @CacheEvict(value = "rule-versions", key = "#ruleKey + ':' + #version")
    public void evictRuleVersion(String ruleKey, Integer version) {
        log.info("清除规则版本缓存: ruleKey={}, version={}", ruleKey, version);
    }

    /**
     * 清除指定目标的灰度配置缓存
     *
     * @param targetType 目标类型
     * @param targetKey  目标Key
     */
    @CacheEvict(value = "grayscale-configs", key = "#targetType + ':' + #targetKey")
    public void evictGrayscaleConfig(String targetType, String targetKey) {
        log.info("清除灰度配置缓存: targetType={}, targetKey={}", targetType, targetKey);
    }

    /**
     * 清除所有缓存
     */
    @CacheEvict(value = {"rules", "compiled-scripts", "rule-versions", "grayscale-configs"}, allEntries = true)
    public void evictAll() {
        log.info("清除所有缓存");
    }
}
