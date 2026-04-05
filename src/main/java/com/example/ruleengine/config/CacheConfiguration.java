package com.example.ruleengine.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 缓存配置
 */
@Configuration
@EnableCaching
public class CacheConfiguration {

    /**
     * 特征缓存（用于 FeatureProviderService）
     */
    @Bean("featureCache")
    public Cache<String, Object> featureCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    /**
     * 缓存管理器
     */
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        List<CaffeineCache> caches = new ArrayList<>();

        // 规则元数据缓存（L2）
        caches.add(buildCache("rules", 5000, 10, TimeUnit.MINUTES));

        // 编译后的脚本缓存（L1）
        caches.add(buildCache("compiled-scripts", 1000, 5, TimeUnit.MINUTES));

        // 决策流缓存
        caches.add(buildCache("decision-flows", 5000, 10, TimeUnit.MINUTES));

        // 规则版本缓存（按 ruleKey+version 维度，版本发布不频繁，TTL 5 分钟）
        caches.add(buildCache("rule-versions", 5000, 5, TimeUnit.MINUTES));

        // 决策流版本缓存（按 flowKey+version 维度，TTL 5 分钟）
        caches.add(buildCache("flow-versions", 5000, 5, TimeUnit.MINUTES));

        // 灰度配置缓存（按 targetType+targetKey 维度，策略可能随时变更，TTL 10 秒）
        caches.add(buildCache("grayscale-configs", 5000, 10, TimeUnit.SECONDS));

        cacheManager.setCaches(caches);
        return cacheManager;
    }

    /**
     * 构建 Caffeine 缓存
     */
    private CaffeineCache buildCache(String name, int maxSize, long expireAfter, TimeUnit timeUnit) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireAfter, timeUnit)
                .recordStats()
                .build());
    }
}
