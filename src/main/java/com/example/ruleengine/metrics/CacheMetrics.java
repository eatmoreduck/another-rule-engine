package com.example.ruleengine.metrics;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 缓存监控指标
 */
@Component
@RequiredArgsConstructor
public class CacheMetrics {

    private final CacheManager cacheManager;

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();

        org.springframework.cache.Cache cache = cacheManager.getCache("rules");
        if (cache instanceof CaffeineCache) {
            CaffeineCache caffeineCache = (CaffeineCache) cache;
            Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();

            CacheStats cacheStats = nativeCache.stats();

            stats.put("hitRate", cacheStats.hitRate());
            stats.put("hitCount", cacheStats.hitCount());
            stats.put("missCount", cacheStats.missCount());
            stats.put("evictionCount", cacheStats.evictionCount());
            stats.put("size", nativeCache.estimatedSize());
        }

        return stats;
    }

    /**
     * 获取所有缓存统计
     */
    public Map<String, Map<String, Object>> getAllCacheStats() {
        Map<String, Map<String, Object>> allStats = new HashMap<>();

        String[] cacheNames = {"rules", "compiled-scripts"};
        for (String cacheName : cacheNames) {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            if (cache instanceof CaffeineCache) {
                CaffeineCache caffeineCache = (CaffeineCache) cache;
                Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
                CacheStats cacheStats = nativeCache.stats();

                Map<String, Object> stats = new HashMap<>();
                stats.put("hitRate", cacheStats.hitRate());
                stats.put("hitCount", cacheStats.hitCount());
                stats.put("missCount", cacheStats.missCount());
                stats.put("evictionCount", cacheStats.evictionCount());
                stats.put("size", nativeCache.estimatedSize());

                allStats.put(cacheName, stats);
            }
        }

        return allStats;
    }
}
