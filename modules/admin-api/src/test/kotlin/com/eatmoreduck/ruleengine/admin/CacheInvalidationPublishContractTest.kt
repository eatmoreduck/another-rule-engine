package com.eatmoreduck.ruleengine.admin

import cn.dev33.satoken.filter.SaFirewallCheckFilterForJakartaServlet
import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet
import com.eatmoreduck.ruleengine.shared.cache.CacheInvalidationCodec
import com.eatmoreduck.ruleengine.shared.cache.CacheInvalidationEvent
import com.eatmoreduck.ruleengine.shared.cache.CacheInvalidationTopics
import com.eatmoreduck.ruleengine.shared.cache.CacheInvalidationType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 缓存失效发布契约测试（Testcontainers PG16 + Redis7，完整上下文 + 真实 Redis 广播）：
 * admin-api 成功变更分支 → AFTER_COMMIT 发布 → 契约频道上可收到正确编码的事件，
 * 以及登录会话键真实落 Redis（decision-api 共享同一库即可校验本服务 token）。
 * 无 Docker 环境自动跳过整个类。
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@SpringBootTest
@DisplayName("阶段 5：缓存失效发布——admin 变更 → 契约频道广播")
class CacheInvalidationPublishContractTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("ruleengine")
                .withUsername("test")
                .withPassword("test")

        @Container
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer("redis:7").withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("ruleengine.storage.url", postgres::getJdbcUrl)
            registry.add("ruleengine.storage.username", postgres::getUsername)
            registry.add("ruleengine.storage.password", postgres::getPassword)
            registry.add("spring.data.redis.url") { "redis://${redis.host}:${redis.getMappedPort(6379)}" }
        }

        /** 测试用订阅队列（订阅侧身份模拟 decision-api） */
        @JvmStatic
        val received = LinkedBlockingQueue<CacheInvalidationEvent>()

        @JvmStatic
        var listenerContainer: RedisMessageListenerContainer? = null

        @JvmStatic
        @AfterAll
        fun stopListenerContainer() {
            listenerContainer?.stop()
        }
    }

    @Autowired
    lateinit var applicationContext: WebApplicationContext

    @Autowired
    lateinit var connectionFactory: RedisConnectionFactory

    @Autowired
    lateinit var stringRedisTemplate: StringRedisTemplate

    /** MockMvc 懒初始化（Sa-Token 过滤器显式挂载，口径同 AdminApiContractTest） */
    private val mockMvc: MockMvc by lazy {
        val builder = MockMvcBuilders.webAppContextSetup(applicationContext)
        builder.addFilters<DefaultMockMvcBuilder>(
            SaFirewallCheckFilterForJakartaServlet(),
            SaTokenContextFilterForJakartaServlet(),
        )
        builder.build()
    }

    private val token: String by lazy { login() }

    private fun login(): String {
        val body =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""{"username":"admin","password":"admin123"}"""),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
                .response
                .contentAsString
        return com.eatmoreduck.ruleengine.dsl.DslJson.mapper
            .readTree(body)
            .get("token")
            .asText()
    }

    private fun post(
        path: String,
        body: String,
    ) = mockMvc.perform(
        MockMvcRequestBuilders
            .post(path)
            .header("Authorization", token)
            .contentType("application/json")
            .content(body),
    )

    /** 收一条广播事件（超时失败，附带已收事件便于排障） */
    private fun awaitEvent(description: String): CacheInvalidationEvent {
        val event = received.poll(5, TimeUnit.SECONDS)
        assertNotNull(event, "5s 内未收到广播事件($description); 已收到: $received")
        return event
    }

    @Test
    @Order(1)
    @DisplayName("订阅契约频道成功（Redis 可用，监听器容器直接启动）")
    fun startListener() {
        listenerContainer =
            RedisMessageListenerContainer().apply {
                setConnectionFactory(this@CacheInvalidationPublishContractTest.connectionFactory)
                addMessageListener(
                    { message, _ ->
                        message.body?.toString(Charsets.UTF_8)?.let { raw ->
                            CacheInvalidationCodec.decodeOrNull(raw)?.let(received::add)
                        }
                    },
                    ChannelTopic(CacheInvalidationTopics.CACHE_INVALIDATE),
                )
            }
        // 测试手动构建（非 Spring 托管）：需显式完成 InitializingBean 初始化
        listenerContainer!!.afterPropertiesSet()
        listenerContainer!!.start()
        assertTrue(listenerContainer!!.isRunning)
    }

    @Test
    @Order(2)
    @DisplayName("创建规则 → RULE 事件广播")
    fun ruleCreatePublishesRuleInvalidation() {
        post(
            "/api/v1/rules",
            """{"ruleKey":"pub_rule","ruleName":"发布契约规则","groovyScript":"return 'PASS'"}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)

        val event = awaitEvent("创建规则")
        assertEquals(CacheInvalidationType.RULE, event.type)
        assertEquals("pub_rule", event.key)
        assertEquals("admin-api", event.source)
    }

    @Test
    @Order(3)
    @DisplayName("规则启停 → RULE 事件广播")
    fun ruleTogglePublishesRuleInvalidation() {
        post("/api/v1/rules/pub_rule/disable", "").andExpect(MockMvcResultMatchers.status().isOk)
        assertEquals(CacheInvalidationType.RULE, awaitEvent("禁用规则").type)
    }

    @Test
    @Order(4)
    @DisplayName("名单新增 → NAME_LIST 事件广播（listKey 缺省 GLOBAL）")
    fun nameListCreatePublishesNameListInvalidation() {
        post(
            "/api/v1/name-list",
            """{"listType":"BLACK","keyType":"IP","keyValue":"10.99.99.99","reason":"契约"}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)

        val event = awaitEvent("名单新增")
        assertEquals(CacheInvalidationType.NAME_LIST, event.type)
        assertEquals("GLOBAL", event.key)
    }

    @Test
    @Order(5)
    @DisplayName("登录 token 会话落 Redis（跨服务共享的数据基础）")
    fun loginSessionLandsInRedis() {
        // token 值作为键段被 Sa-Token 写入 Redis（token→loginId 映射键），前缀随版本无关断言：
        // 以 token 值模糊匹配，decision-api 持同一 Redis 即可校验本 token
        val tokenKeys = stringRedisTemplate.keys("*$token*")
        assertTrue(tokenKeys.isNotEmpty(), "登录后 Redis 中应存在携带该 token 的会话键，实际为空")
    }
}
