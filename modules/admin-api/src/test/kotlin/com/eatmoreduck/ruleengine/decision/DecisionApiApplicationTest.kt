package com.eatmoreduck.ruleengine.decision

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * 阶段 0 冒烟遗留：Boot 4.1.1 + Kotlin 2.4.20 上下文可正常装配（含 decision 链路的全部 bean）。
 *
 * 上下文含 storage 装配（DataSource/Exposed/Flyway），本测试用 H2 内存库 + 空 Flyway 位置
 * 仅为满足 bean 装配（真实 PostgreSQL 全链路由 Testcontainers 契约测试覆盖）。
 */
@SpringBootTest(classes = [com.eatmoreduck.ruleengine.admin.AdminApiApplication::class])
class DecisionApiApplicationTest {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("ruleengine.storage.url") { "jdbc:h2:mem:decision-app-test;DB_CLOSE_DELAY=-1" }
            // 空迁移位置：Flyway 容忍不存在的 location（跳过并告警），仅避免在 H2 上执行 PG 方言 DDL
            registry.add("ruleengine.storage.flyway-locations") { "classpath:db/migration-none" }
        }
    }

    @Test
    fun `spring context loads`() {
        // 装配冒烟：决策链路 bean（引擎/路由/服务/控制器/日志缓冲）全部就位
    }
}
