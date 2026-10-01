package com.eatmoreduck.ruleengine.admin.config

import cn.dev33.satoken.dao.SaTokenDao
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate
import com.eatmoreduck.ruleengine.shared.satoken.SaTokenOutageFallbackDao
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Redis 集成装配（阶段 5）：Sa-Token 跨服务会话。
 *
 * 会话存储链路：
 * - 官方 [SaTokenDaoForRedisTemplate] 由 sa-token-redis-template 的 AutoConfiguration.imports
 *   自动装配（对象序列化经 SaManager 的 Jackson 3 模板，Boot 4 starter 自带）；
 * - 本类将其包装为 [SaTokenOutageFallbackDao] 并置 @Primary——Sa-Token 的 SaBeanInject
 *   按 @Primary 把包装器注入 SaManager：Redis 正常时会话落 Redis（decision-api 可校验
 *   本服务颁发的 token），Redis 不可用时逐操作降级内存（登录/鉴权照常，token 仅本实例有效）。
 *
 * 与 decision-api 的 RedisConfiguration 同构（决策侧另有失效广播订阅容器）。
 */
@Configuration(proxyBeanMethods = false)
class RedisConfiguration {
    @Bean
    @Primary
    fun saTokenDao(redisDao: SaTokenDaoForRedisTemplate): SaTokenDao = SaTokenOutageFallbackDao(redisDao)
}
