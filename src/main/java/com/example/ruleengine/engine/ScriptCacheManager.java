package com.example.ruleengine.engine;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 脚本缓存管理器
 * Source: RESEARCH.md 模式2 + CONTEXT.md 决策 D-05, D-06
 *
 * 功能：
 * 1. 使用 ConcurrentHashMap 实现线程安全的脚本缓存（D-05）
 * 2. 定期清理过期缓存防止 Metaspace 泄漏（D-06）
 * 3. 限制缓存大小防止内存溢出
 */
@Component
public class ScriptCacheManager {

    private static final int MAX_CACHE_SIZE = 1000;
    private static final long CACHE_EXPIRE_HOURS = 24;

    // D-05: ConcurrentHashMap 保证线程安全
    private final ConcurrentHashMap<String, CacheEntry> scriptCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> lastAccessTime = new ConcurrentHashMap<>();

    /**
     * 从缓存获取已编译的脚本
     */
    public Class<?> get(String ruleId, String scriptHash) {
        String key = buildCacheKey(ruleId, scriptHash);
        CacheEntry entry = scriptCache.get(key);

        if (entry != null) {
            // 更新最后访问时间
            lastAccessTime.put(key, System.currentTimeMillis());
            return entry.getScriptClass();
        }

        return null;
    }

    /**
     * 将编译后的脚本放入缓存
     */
    public void put(String ruleId, String scriptHash, Class<?> scriptClass) {
        String key = buildCacheKey(ruleId, scriptHash);

        // 检查缓存大小限制
        if (scriptCache.size() >= MAX_CACHE_SIZE) {
            cleanOldestEntries(100); // 清理最旧的100个条目
        }

        scriptCache.put(key, new CacheEntry(scriptClass));
        lastAccessTime.put(key, System.currentTimeMillis());
    }

    /**
     * D-06: 定期清理过期缓存
     * 每小时执行一次
     */
    @Scheduled(fixedRate = 3600000) // 1小时
    public void cleanExpiredCache() {
        long now = System.currentTimeMillis();
        long expireTime = CACHE_EXPIRE_HOURS * 3600000;

        scriptCache.entrySet().removeIf(entry -> {
            Long lastAccess = lastAccessTime.get(entry.getKey());
            if (lastAccess != null && (now - lastAccess) > expireTime) {
                lastAccessTime.remove(entry.getKey());
                return true; // 移除过期条目
            }
            return false;
        });
    }

    /**
     * 清理最旧的缓存条目
     */
    private void cleanOldestEntries(int count) {
        scriptCache.keySet().stream()
            .sorted(Comparator.comparing(key -> lastAccessTime.getOrDefault(key, 0L)))
            .limit(count)
            .forEach(key -> {
                scriptCache.remove(key);
                lastAccessTime.remove(key);
            });
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(String ruleId, String scriptHash) {
        return ruleId + ":" + scriptHash;
    }

    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return scriptCache.size();
    }

    /**
     * 清空所有缓存
     */
    public void clear() {
        scriptCache.clear();
        lastAccessTime.clear();
    }

    /**
     * 缓存条目
     */
    private static class CacheEntry {
        private final Class<?> scriptClass;

        public CacheEntry(Class<?> scriptClass) {
            this.scriptClass = scriptClass;
        }

        public Class<?> getScriptClass() {
            return scriptClass;
        }
    }
}
