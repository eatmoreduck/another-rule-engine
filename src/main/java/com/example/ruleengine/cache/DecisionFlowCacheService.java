package com.example.ruleengine.cache;

import com.example.ruleengine.domain.DecisionFlow;
import com.example.ruleengine.repository.DecisionFlowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 决策流缓存服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DecisionFlowCacheService {

    private final DecisionFlowRepository decisionFlowRepository;

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
     * 清除指定决策流缓存
     */
    @CacheEvict(value = "decision-flows", key = "#flowKey")
    public void evictFlow(String flowKey) {
        log.info("清除决策流缓存: flowKey={}", flowKey);
    }

    /**
     * 清除所有决策流缓存
     */
    @CacheEvict(value = "decision-flows", allEntries = true)
    public void evictAll() {
        log.info("清除所有决策流缓存");
    }
}
