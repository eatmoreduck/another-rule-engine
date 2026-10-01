package com.eatmoreduck.ruleengine.decision.cache

import com.eatmoreduck.ruleengine.decision.core.FeatureResolutionService
import com.eatmoreduck.ruleengine.decision.core.GrayscaleRouter
import com.eatmoreduck.ruleengine.decision.repo.NameListLookup
import com.eatmoreduck.ruleengine.shared.cache.CacheInvalidationCodec
import com.eatmoreduck.ruleengine.shared.cache.CacheInvalidationType
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.stereotype.Component

/**
 * 缓存失效广播订阅方（阶段 5）：消费 admin-api 发布的 [CacheInvalidationTopics.CACHE_INVALIDATE]
 * 频道消息，按事件类别失效决策侧对应 Caffeine 缓存层。
 *
 * fail-safe 约定（决策链路 50ms 红线）：
 * - [onMessage] 全程 try/catch，任何异常只记日志，绝不向上抛（Spring Data Redis 的监听器异常
 *   会中断订阅任务）；坏载荷/未知事件类型（滚动发布期）静默忽略；
 * - 失效动作本身幂等（invalidate/invalidateAll），乱序、重复、迟到事件无害——
 *   最多触发一次多余的重查；事件先于事务提交到达的极端竞态由缓存 TTL 兜底；
 * - Redis 不可用时订阅任务由 Lettuce/Spring 容器自动重连，期间仅靠 TTL 兜底（最长 30s）。
 */
@Component
class CacheInvalidationSubscriber(
    private val grayscaleRouter: GrayscaleRouter,
    private val featureResolutionService: FeatureResolutionService,
    private val nameListLookup: NameListLookup,
) : MessageListener {
    private val log = LoggerFactory.getLogger(CacheInvalidationSubscriber::class.java)

    override fun onMessage(
        message: Message,
        pattern: ByteArray?,
    ) {
        try {
            val body = message.body ?: return
            val event = CacheInvalidationCodec.decodeOrNull(body.toString(Charsets.UTF_8))
            if (event == null) {
                // 坏载荷或未知事件类型（向前兼容口径见 Codec）：忽略
                return
            }
            apply(event.type, event.key)
            log.debug(
                "缓存失效广播已生效: type={}, key={}, source={}, occurredAt={}",
                event.type,
                event.key,
                event.source,
                event.occurredAt,
            )
        } catch (e: Exception) {
            // 订阅侧任何异常不得影响决策链路（容器线程存活 > 单条消息处理成功）
            log.error("缓存失效广播处理异常, 忽略该条消息", e)
        }
    }

    /** 按类别路由失效动作（key 语义见各处理器的契约注释） */
    private fun apply(
        type: CacheInvalidationType,
        key: String?,
    ) {
        when (type) {
            CacheInvalidationType.RULE -> grayscaleRouter.invalidateRule(key)

            CacheInvalidationType.FLOW -> grayscaleRouter.invalidateFlow(key)

            CacheInvalidationType.GRAYSCALE -> grayscaleRouter.invalidateGrayscale(key)

            // 特征目录含别名链式映射，无法按键圈定，统一整层失效
            CacheInvalidationType.FEATURE -> featureResolutionService.invalidateAll()

            // 名单缓存键为四元组，无法按 listKey 圈定，统一整层失效
            CacheInvalidationType.NAME_LIST -> nameListLookup.invalidateAll()
        }
    }
}
