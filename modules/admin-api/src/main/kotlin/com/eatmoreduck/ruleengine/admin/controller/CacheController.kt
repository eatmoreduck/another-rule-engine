package com.eatmoreduck.ruleengine.admin.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.eatmoreduck.ruleengine.admin.cache.CacheManagementService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 缓存管理 REST API 控制器（路径/方法与旧 CacheController 一致：/api/v1/cache 族，
 * 类级登录校验，无方法级权限码）。
 *
 * 行为适配新架构：清缓存端点发 Redis pub/sub 失效广播（decision-api 订阅失效本地缓存），
 * 不直接操作决策侧内存；统计端点仅返回 admin-api 本机可观测的编译缓存指标。
 */
@RestController
@RequestMapping("/api/v1/cache")
@SaCheckLogin
class CacheController(
    private val cacheManagementService: CacheManagementService,
) {
    private val log = LoggerFactory.getLogger(CacheController::class.java)

    /** 获取缓存统计：GET /api/v1/cache/stats */
    @GetMapping("/stats")
    fun getCacheStats(): ResponseEntity<Map<String, Map<String, Any>>> {
        log.info("获取缓存统计")
        return ResponseEntity.ok(cacheManagementService.getCacheStats())
    }

    /** 清除指定规则缓存：POST /api/v1/cache/evict/{ruleKey} */
    @PostMapping("/evict/{ruleKey}")
    fun evictRule(
        @PathVariable ruleKey: String,
    ): ResponseEntity<Void> {
        log.info("清除规则缓存: ruleKey={}", ruleKey)
        cacheManagementService.evictRule(ruleKey)
        return ResponseEntity.ok().build()
    }

    /** 清除所有缓存：POST /api/v1/cache/evict-all */
    @PostMapping("/evict-all")
    fun evictAll(): ResponseEntity<Void> {
        log.info("清除所有缓存")
        cacheManagementService.evictAll()
        return ResponseEntity.ok().build()
    }

    /** 手动触发缓存预热：POST /api/v1/cache/warm-up（空操作，与旧实现一致） */
    @PostMapping("/warm-up")
    fun warmUpCache(): ResponseEntity<Void> {
        log.info("手动触发缓存预热")
        cacheManagementService.warmUpCache()
        return ResponseEntity.ok().build()
    }
}
