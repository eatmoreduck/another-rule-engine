package com.eatmoreduck.ruleengine.admin.cache

import com.eatmoreduck.ruleengine.engine.GroovyScriptEngine
import com.eatmoreduck.ruleengine.shared.cache.CacheInvalidationType
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

/**
 * 缓存管理服务（对齐旧 CacheController 依赖的 CacheMetrics / RuleCacheService 职责，
 * 适配新架构的分布式缓存拓扑）。
 *
 * 新旧架构差异：旧单体里规则缓存与编译缓存在同一进程内，可直接清本地 Caffeine；
 * 新架构规则/灰度缓存位于 decision-api 各实例内存，admin-api 只能经
 * [publishInvalidation] 发 Redis pub/sub 失效广播（阶段 5 机制）让决策侧自行失效，
 * TTL 兜底始终存在。admin-api 本机可观测、可清理的只有脚本编译缓存（GroovyScriptEngine）。
 */
@Service
class CacheManagementService(
    private val scriptEngine: GroovyScriptEngine,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * 缓存统计（字段名与旧 CacheMetrics 逐字一致：hitRate/hitCount/missCount/evictionCount/size）。
     *
     * 与旧契约的偏差：旧响应含 "rules" 与 "compiled-scripts" 两键（单体进程内两套缓存）；
     * 新架构 "rules" 族缓存在 decision-api 进程内、admin-api 无法观测，故仅返回
     * "compiled-scripts"（本机脚本编译缓存）。前端无该接口消费方（frontend/src 未调用）。
     */
    fun getCacheStats(): Map<String, Map<String, Any>> {
        val stats = scriptEngine.cacheStats
        return linkedMapOf(
            COMPILED_SCRIPTS_CACHE to
                linkedMapOf(
                    "hitRate" to stats.hitRate(),
                    "hitCount" to stats.hitCount(),
                    "missCount" to stats.missCount(),
                    "evictionCount" to stats.evictionCount(),
                    "size" to scriptEngine.cacheSize,
                ),
        )
    }

    /**
     * 清除指定规则缓存：广播 RULE 类失效事件（key = ruleKey），
     * decision 侧失效规则主行 + 版本载荷两层缓存。
     */
    fun evictRule(ruleKey: String) {
        log.info("清除规则缓存(广播失效事件): ruleKey={}", ruleKey)
        eventPublisher.publishInvalidation(CacheInvalidationType.RULE, ruleKey)
    }

    /**
     * 清除所有缓存（对齐旧 evictAll 的作用面：rules + rule-versions + grayscale-configs +
     * compiled-scripts）：
     * - RULE(null) 广播 → 决策侧规则主行 + 版本载荷缓存整层失效（对应旧 rules/rule-versions）；
     * - GRAYSCALE(null) 广播 → 决策侧灰度配置缓存整层失效（对应旧 grayscale-configs）；
     * - 本机 [GroovyScriptEngine.discardAll] → 编译缓存与隔离类加载器整体释放（对应旧 compiled-scripts）。
     */
    fun evictAll() {
        log.info("清除所有缓存(广播失效事件 + 本机编译缓存重置)")
        eventPublisher.publishInvalidation(CacheInvalidationType.RULE, null)
        eventPublisher.publishInvalidation(CacheInvalidationType.GRAYSCALE, null)
        scriptEngine.discardAll()
    }

    /**
     * 手动触发缓存预热：与旧实现一致为空操作（旧控制器注释即声明未接线 CacheWarmer），
     * 保留端点以维持路径契约。
     */
    fun warmUpCache() {
        log.info("手动触发缓存预热(空操作, 与旧实现一致)")
    }

    companion object {
        private val log = LoggerFactory.getLogger(CacheManagementService::class.java)

        /** 统计响应中的编译缓存键名（与旧 CacheMetrics 的缓存名一致） */
        const val COMPILED_SCRIPTS_CACHE = "compiled-scripts"
    }
}
