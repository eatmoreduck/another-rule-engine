package com.example.ruleengine.cache;

import com.example.ruleengine.domain.DecisionFlow;
import com.example.ruleengine.domain.DecisionFlowVersion;
import com.example.ruleengine.repository.DecisionFlowRepository;
import com.example.ruleengine.repository.DecisionFlowVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 决策流缓存服务
 * 支持决策流主数据缓存和版本维度缓存
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DecisionFlowCacheService {

    private final DecisionFlowRepository decisionFlowRepository;
    private final DecisionFlowVersionRepository decisionFlowVersionRepository;

    /**
     * 从缓存获取决策流
     */
    @Cacheable(value = "decision-flows", key = "#flowKey")
    public DecisionFlow getFlow(String flowKey) {
        log.debug("缓存未命中，从数据库加载决策流: flowKey={}", flowKey);
        return decisionFlowRepository.findByFlowKey(flowKey).orElse(null);
    }

    /**
     * 从缓存获取启用的决策流
     */
    @Cacheable(value = "decision-flows", key = "'enabled:' + #flowKey")
    public DecisionFlow getEnabledFlow(String flowKey) {
        log.debug("缓存未命中，从数据库加载启用决策流: flowKey={}", flowKey);
        return decisionFlowRepository.findByFlowKeyAndEnabledTrue(flowKey).orElse(null);
    }

    /**
     * 从缓存获取指定版本的决策流
     * 缓存 key: flow-version:{flowKey}:{version}，TTL 5 分钟
     *
     * @param flowKey 决策流Key
     * @param version 版本号
     * @return 决策流版本，不存在返回 null
     */
    @Cacheable(value = "flow-versions", key = "#flowKey + ':' + #version")
    public DecisionFlowVersion getFlowVersion(String flowKey, Integer version) {
        log.debug("缓存未命中，从数据库加载决策流版本: flowKey={}, version={}", flowKey, version);
        return decisionFlowVersionRepository.findByFlowKeyAndVersion(flowKey, version).orElse(null);
    }

    /**
     * 清除指定决策流缓存
     */
    @CacheEvict(value = "decision-flows", key = "#flowKey")
    public void evictFlow(String flowKey) {
        log.info("清除决策流缓存: flowKey={}", flowKey);
    }

    /**
     * 清除指定决策流版本缓存
     *
     * @param flowKey 决策流Key
     * @param version 版本号
     */
    @CacheEvict(value = "flow-versions", key = "#flowKey + ':' + #version")
    public void evictFlowVersion(String flowKey, Integer version) {
        log.info("清除决策流版本缓存: flowKey={}, version={}", flowKey, version);
    }

    /**
     * 清除所有决策流缓存（含版本缓存）
     */
    @CacheEvict(value = {"decision-flows", "flow-versions"}, allEntries = true)
    public void evictAll() {
        log.info("清除所有决策流缓存");
    }
}
