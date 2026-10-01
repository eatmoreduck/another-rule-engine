package com.eatmoreduck.ruleengine.admin.cache

import com.eatmoreduck.ruleengine.shared.cache.CacheInvalidationEvent
import com.eatmoreduck.ruleengine.shared.cache.CacheInvalidationTopics
import com.eatmoreduck.ruleengine.shared.cache.CacheInvalidationType
import org.springframework.context.ApplicationEventPublisher

/**
 * 发布决策侧缓存失效事件（阶段 5）。
 *
 * 语义：同步 Spring 应用事件——[CacheInvalidationForwarder] 以 AFTER_COMMIT 事务监听承接，
 * 事务提交后转发 Redis 广播频道；无事务时兜底直接转发。Redis 不可用由转发器只记日志，
 * 决策侧缓存由 TTL 兜底，发布方零失败面。
 *
 * 各业务 Service 在**成功变更分支**调用（守卫拒绝/幂等未变更分支不发布）。
 */
fun ApplicationEventPublisher.publishInvalidation(
    type: CacheInvalidationType,
    key: String?,
) {
    publishEvent(CacheInvalidationEvent.now(type, key, CacheInvalidationTopics.SOURCE_ADMIN_API))
}
