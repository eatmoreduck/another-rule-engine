package com.eatmoreduck.ruleengine.decision

import cn.dev33.satoken.dao.SaTokenDao
import com.eatmoreduck.ruleengine.shared.satoken.SaTokenOutageFallbackDao
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 优雅降级契约（阶段 5 硬性要求）：Redis 地址指向不可达端口时——
 * - 应用上下文照常启动（订阅容器由带重试的生命周期接管，不 FailFast）；
 * - Sa-Token 会话操作逐级降级内存（登录/鉴权功能正常，token 不跨服务）；
 * - 决策链路组件照常装配。
 *
 * admin-api 侧的同等行为由其现有测试套件隐式回归（全部契约测试在未配 Redis 的
 * localhost:6379 上运行，登录链路经同一降级包装器）。
 */
@SpringBootTest
@DisplayName("阶段 5：Redis 不可用时上下文照常启动 + 会话降级内存")
class RedisUnavailableDegradationTest {
    companion object {
        /** 本机必然无监听的端口（模拟 Redis 不可达：连接拒绝） */
        const val DEAD_REDIS_URL: String = "redis://localhost:59999"

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("ruleengine.storage.url") { "jdbc:h2:mem:decision-degrade-test;DB_CLOSE_DELAY=-1" }
            // 空迁移位置：Flyway 容忍不存在的 location（跳过并告警），仅避免在 H2 上执行 PG 方言 DDL
            registry.add("ruleengine.storage.flyway-locations") { "classpath:db/migration-none" }
            registry.add("spring.data.redis.url") { DEAD_REDIS_URL }
        }
    }

    @Autowired
    lateinit var saTokenDao: SaTokenDao

    @Autowired
    lateinit var listenerContainer: RedisMessageListenerContainer

    @Test
    fun `context loads with unreachable redis`() {
        // 上下文装配即断言：无异常抛出即通过
        assertTrue(listenerContainer.isActive, "订阅容器应完成初始化（启动重试由生命周期组件后台承担）")
    }

    @Test
    fun `sa-token dao degrades to in-memory storage`() {
        assertIs<SaTokenOutageFallbackDao>(saTokenDao)

        // Redis 不可达：写入降级内存成功（熔断阈值内每次操作先尝试 Redis，连接拒绝快速失败）
        saTokenDao.setObject("degrade:key", mapOf("k" to "v"), 60)
        assertEquals(mapOf("k" to "v"), saTokenDao.getObject("degrade:key"))

        // 会话层同样降级可用
        val session =
            cn.dev33.satoken.session
                .SaSession("degrade:session")
                .setLoginId(1L)
        saTokenDao.setSession(session, 60)
        assertEquals(1L, saTokenDao.getSession("degrade:session")?.loginId)
    }
}
