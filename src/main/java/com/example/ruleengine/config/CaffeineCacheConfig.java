package com.example.ruleengine.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 缓存配置
 * Source: RESEARCH.md 标准技术栈 + CONTEXT.md 决策 D-13
 *
 * 功能：
 * 1. 配置本地缓存用于高频特征
 * 2. 配置脚本缓存（已在 ScriptCacheManager 实现）
 * 3. 支持缓存统计和监控
 */
@Configuration
@EnableCaching
public class CaffeineCacheConfig {

    /**
     * 特征缓存配置
     * D-13: 使用 Caffeine 实现本地缓存
     */
    @Bean
    public Cache<String, Object> featureCache() {
        return Caffeine.newBuilder()
            .maximumSize(10_000)  // 最大缓存 10000 个特征
            .expireAfterWrite(30, TimeUnit.MINUTES)  // 30 分钟过期
            .recordStats()  // 启用统计
            .build();
    }

    /**
     * Spring Cache 抽象的 CacheManager
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .recordStats());
        return cacheManager;
    }
}
