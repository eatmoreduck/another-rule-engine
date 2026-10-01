package com.eatmoreduck.ruleengine.admin.cache

import com.eatmoreduck.ruleengine.shared.cache.CacheInvalidationCodec
import com.eatmoreduck.ruleengine.shared.cache.CacheInvalidationEvent
import com.eatmoreduck.ruleengine.shared.cache.CacheInvalidationTopics
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 缓存失效事件转发器（阶段 5）：把各业务 Service 发出的 Spring 应用事件
 * （[CacheInvalidationEvent]）在**事务提交后**转发到 Redis 广播频道
 * [CacheInvalidationTopics.CACHE_INVALIDATE]，decision-api 订阅失效本地 Caffeine。
 *
 * 设计要点：
 * - AFTER_COMMIT：事务提交前广播存在竞态——决策侧可能先收到事件、再从库中读到旧值并回填缓存，
 *   失效被"作废"。提交后广播保证订阅方重查时可见新数据；极端乱序仍由 TTL 兜底；
 * - fallbackExecution：理论上全部变更路径都在事务内，防御性兜底无事务场景直接转发；
 * - 发布失败（Redis 不可用）只记 WARN，绝不影响业务主流程——失效广播是优化路径，
 *   TTL 兜底始终存在。
 *
 * 发布时机为同步 Spring 事件 + 事务同步回调，业务调用方零阻塞、零新增依赖
 * （只依赖 ApplicationEventPublisher，不感知 Redis）。
 */
@Component
class CacheInvalidationForwarder(
    private val stringRedisTemplate: StringRedisTemplate,
) {
    private val log = LoggerFactory.getLogger(CacheInvalidationForwarder::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onCacheInvalidation(event: CacheInvalidationEvent) {
        try {
            stringRedisTemplate.convertAndSend(
                CacheInvalidationTopics.CACHE_INVALIDATE,
                CacheInvalidationCodec.encode(event),
            )
        } catch (e: DataAccessException) {
            // Redis 不可用：失效广播丢失，决策侧缓存由 TTL 兜底（最长 30s 延迟）
            log.warn("缓存失效事件发布失败(Redis 不可用?), 决策侧将由 TTL 兜底: type={}, key={}", event.type, event.key)
        }
    }
}
