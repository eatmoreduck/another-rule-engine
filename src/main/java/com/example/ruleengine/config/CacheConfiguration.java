package com.example.ruleengine.config;

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
