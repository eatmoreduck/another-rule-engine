package com.example.ruleengine.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.ruleengine.cache.RuleCacheService;
import com.example.ruleengine.metrics.CacheMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 缓存管理 REST API 控制器
 */
@RestController
@RequestMapping("/api/v1/cache")
@RequiredArgsConstructor
@Slf4j
@SaCheckLogin
public class CacheController {

    private final CacheMetrics cacheMetrics;
    private final RuleCacheService ruleCacheService;

    /**
     * 获取缓存统计
     * GET /api/v1/cache/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Map<String, Object>>> getCacheStats() {
        log.info("获取缓存统计");
        return ResponseEntity.ok(cacheMetrics.getAllCacheStats());
    }

    /**
     * 清除指定规则缓存
     * POST /api/v1/cache/evict/{ruleKey}
     */
    @PostMapping("/evict/{ruleKey}")
    public ResponseEntity<Void> evictRule(@PathVariable String ruleKey) {
        log.info("清除规则缓存: ruleKey={}", ruleKey);
        ruleCacheService.evictRule(ruleKey);
        return ResponseEntity.ok().build();
    }

    /**
     * 清除所有缓存
     * POST /api/v1/cache/evict-all
     */
    @PostMapping("/evict-all")
    public ResponseEntity<Void> evictAll() {
        log.info("清除所有缓存");
        ruleCacheService.evictAll();
        return ResponseEntity.ok().build();
    }

    /**
     * 手动触发缓存预热
     * POST /api/v1/cache/warm-up
     */
    @PostMapping("/warm-up")
    public ResponseEntity<Void> warmUpCache() {
        log.info("手动触发缓存预热");
        // 注意：这会触发 CacheWarmer 的 warmUpCache 方法
        return ResponseEntity.ok().build();
    }
}
