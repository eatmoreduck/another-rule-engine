package com.eatmoreduck.ruleengine.decision.config

import cn.dev33.satoken.dao.SaTokenDao
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate
import com.eatmoreduck.ruleengine.decision.cache.CacheInvalidationSubscriber
import com.eatmoreduck.ruleengine.shared.cache.CacheInvalidationTopics
import com.eatmoreduck.ruleengine.shared.satoken.SaTokenOutageFallbackDao
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer

/**
 * Redis 集成装配（阶段 5）：Sa-Token 跨服务会话 + 缓存失效广播订阅。
 *
 * 会话存储链路：
 * - 官方 [SaTokenDaoForRedisTemplate] 由 sa-token-redis-template 的 AutoConfiguration.imports
 *   自动装配（对象序列化经 SaManager 的 Jackson 3 模板，Boot 4 starter 自带）；
 * - 本类将其包装为 [SaTokenOutageFallbackDao] 并置 @Primary——Sa-Token 的 SaBeanInject
 *   按 @Primary 把包装器注入 SaManager：Redis 正常时会话落 Redis（跨服务共享），
 *   Redis 不可用时逐操作降级内存（服务照常运行）。
 *
 * 失效广播链路：[org.springframework.data.redis.listener.RedisMessageListenerContainer]
 * 订阅 [CacheInvalidationTopics.CACHE_INVALIDATE] 频道；Redis 未就绪时启动重试由
 * [CacheInvalidationSubscriptionLifecycle] 承担（应用照常启动），建立后的断线重连由容器与
 * Lettuce 自理，均不阻塞、不影响决策链路。
 */
@Configuration(proxyBeanMethods = false)
class RedisConfiguration {
    @Bean
    @Primary
    fun saTokenDao(redisDao: SaTokenDaoForRedisTemplate): SaTokenDao = SaTokenOutageFallbackDao(redisDao)

    /**
     * 失效广播订阅容器（bean 名对齐 Boot 4 的 DataRedisAnnotationDrivenConfiguration 默认名：
     * 其 @ConditionalOnMissingBean(name="redisMessageListenerContainer") 随之让位，避免双容器）。
     *
     * autoStartup=false——Spring Data Redis 4 的容器在初始订阅失败（Redis 未就绪）时
     * start() 同步抛错并拖垮应用启动，启动时机交由 [CacheInvalidationSubscriptionLifecycle] 带重试接管。
     */
    @Bean
    fun redisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory,
        subscriber: CacheInvalidationSubscriber,
    ): RedisMessageListenerContainer =
        RedisMessageListenerContainer().apply {
            setConnectionFactory(connectionFactory)
            addMessageListener(subscriber, ChannelTopic(CacheInvalidationTopics.CACHE_INVALIDATE))
            isAutoStartup = false
        }

    @Bean
    fun cacheInvalidationSubscriptionLifecycle(
        redisMessageListenerContainer: RedisMessageListenerContainer,
    ): CacheInvalidationSubscriptionLifecycle = CacheInvalidationSubscriptionLifecycle(redisMessageListenerContainer)
}
