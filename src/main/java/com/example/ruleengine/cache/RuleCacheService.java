package com.example.ruleengine.cache;

import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 规则缓存服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleCacheService {

    private final RuleRepository ruleRepository;

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
     * 清除指定规则缓存
     */
    @CacheEvict(value = "rules", key = "#ruleKey")
    public void evictRule(String ruleKey) {
        log.info("清除规则缓存: ruleKey={}", ruleKey);
    }

    /**
     * 清除所有缓存
     */
    @CacheEvict(value = {"rules", "compiled-scripts"}, allEntries = true)
    public void evictAll() {
        log.info("清除所有缓存");
    }
}
