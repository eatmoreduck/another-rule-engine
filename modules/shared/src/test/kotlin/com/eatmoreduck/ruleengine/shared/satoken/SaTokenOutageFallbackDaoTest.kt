package com.eatmoreduck.ruleengine.shared.satoken

import cn.dev33.satoken.SaManager
import cn.dev33.satoken.dao.SaTokenDao
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate
import cn.dev33.satoken.json.SaJsonTemplateForJackson3
import cn.dev33.satoken.session.SaSession
import cn.dev33.satoken.session.SaTerminalInfo
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Sa-Token Redis 会话降级链路集成测试（Testcontainers Redis 7，无 Spring 上下文）：
 * - 会话/字符串/对象三层经真实 Redis 往返，且跨"两个 dao 实例"可见（跨服务共享语义）；
 * - TTL 语义（NEVER_EXPIRE / NOT_VALUE_EXPIRE / 续期）；
 * - 主存储故障时逐操作降级内存 + 熔断 + 半开恢复。
 *
 * 说明：官方 SaTokenDaoForRedisTemplate 的对象序列化经 SaManager.getSaJsonTemplate()
 * （生产环境由 Boot 4 starter 的 sa-token-jackson3 插件注册为 SaJsonTemplateForJackson3），
 * 测试在类级显式安装同一实现，模拟真实运行时条件。
 */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SaTokenOutageFallbackDaoTest {
    companion object {
        @Container
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer("redis:7").withExposedPorts(6379)

        /** 模拟 Boot 4 运行时的 SaJsonTemplate（Jackson 3 多态序列化，@class 标记） */
        @JvmStatic
        @BeforeAll
        fun installJackson3Template() {
            SaManager.setSaJsonTemplate(SaJsonTemplateForJackson3())
        }
    }

    private lateinit var factory: LettuceConnectionFactory

    /** 官方 Redis dao（生产由 Boot 自动装配，测试手动 init，等价） */
    private lateinit var redisDao: SaTokenDaoForRedisTemplate

    /** 模拟 admin-api 进程的 dao */
    private lateinit var adminSideDao: SaTokenOutageFallbackDao

    /** 模拟 decision-api 进程的 dao（独立实例，仅共享 Redis） */
    private lateinit var decisionSideDao: SaTokenOutageFallbackDao

    /** 直连 Redis 的模板（断言"键确实落在 Redis"） */
    private lateinit var rawTemplate: StringRedisTemplate

    @BeforeAll
    fun setUp() {
        factory =
            LettuceConnectionFactory(
                RedisStandaloneConfiguration(redis.host, redis.getMappedPort(6379)),
            )
        factory.afterPropertiesSet()
        factory.start()

        rawTemplate = StringRedisTemplate(factory)

        redisDao = SaTokenDaoForRedisTemplate()
        redisDao.init(factory)

        adminSideDao = SaTokenOutageFallbackDao(redisDao)
        decisionSideDao = SaTokenOutageFallbackDao(redisDao)
    }

    @AfterAll
    fun tearDown() {
        factory.stop()
    }

    // ---------- 跨服务共享语义 ----------

    @Test
    fun `session written by one instance is visible to another instance over redis`() {
        val session =
            SaSession("satoken:login:session:1001")
                .setLoginId(1001L)
                .setToken("token-abc")
        session.set("roleCode", "admin")

        adminSideDao.setSession(session, 60)

        val loaded = decisionSideDao.getSession("satoken:login:session:1001")
        assertNotNull(loaded)
        assertEquals("satoken:login:session:1001", loaded.id)
        assertEquals(1001L, loaded.loginId)
        assertEquals("token-abc", loaded.token)
        assertEquals("admin", loaded.get("roleCode"))
        // 键确实落在 Redis（而非降级内存）：裸模板直查可见
        assertTrue(rawTemplate.hasKey("satoken:login:session:1001"))
    }

    @Test
    fun `session polymorphic payload survives round-trip`() {
        val session = SaSession("satoken:login:session:2002").setLoginId(2002L)
        session.addTerminal(SaTerminalInfo().setDeviceType("pc").setTokenValue("t-2002"))

        adminSideDao.setSession(session, 60)
        val loaded = decisionSideDao.getSession("satoken:login:session:2002")

        assertNotNull(loaded)
        assertEquals(1, loaded.terminalListCopy().size)
        assertEquals("pc", loaded.terminalListCopy()[0].deviceType)
        assertEquals("t-2002", loaded.terminalListCopy()[0].tokenValue)
    }

    @Test
    fun `token string mapping is shared across instances`() {
        adminSideDao.set("satoken:login:token:t-shared", "1001", 60)
        assertEquals("1001", decisionSideDao.get("satoken:login:token:t-shared"))
        assertTrue(rawTemplate.hasKey("satoken:login:token:t-shared"))
    }

    // ---------- TTL 语义 ----------

    @Test
    fun `timeout semantics follow sa-token contract`() {
        // 不存在 → NOT_VALUE_EXPIRE(-2)
        assertEquals(SaTokenDao.NOT_VALUE_EXPIRE, adminSideDao.getTimeout("no-such-key"))

        // 带过期写入 → (0, ttl]
        adminSideDao.set("k:ttl", "v", 120)
        val timeout = adminSideDao.getTimeout("k:ttl")
        assertTrue(timeout in 1..120, "timeout=$timeout")

        // NEVER_EXPIRE 写入 → 永不过期(-1)
        adminSideDao.set("k:persist", "v", SaTokenDao.NEVER_EXPIRE)
        assertEquals(SaTokenDao.NEVER_EXPIRE, adminSideDao.getTimeout("k:persist"))

        // 续期
        adminSideDao.updateTimeout("k:ttl", 500)
        assertTrue(adminSideDao.getTimeout("k:ttl") > 120)
    }

    // ---------- 降级与熔断 ----------

    @Test
    fun `degrades to in-memory per-op when primary throws, then half-open recovers`() {
        val fake = FlakyPrimaryDao()
        val dao = SaTokenOutageFallbackDao(fake, failureThreshold = 2, reopenMillis = 200)

        // 主存储故障：set 走内存成功，且熔断按阈值触发（2 次真实尝试后短路）
        dao.set("dg:key", "v1", 60)
        assertEquals("v1", dao.get("dg:key"))
        assertEquals(2, fake.attempts.get()) // set + get 各尝试一次后熔断打开

        // 熔断窗口内不再触碰主存储
        dao.update("dg:key", "v2")
        assertEquals(2, fake.attempts.get())
        assertEquals("v2", dao.get("dg:key"))

        // 冷却后半开：主存储恢复 → 切回主存储（主存储持独立值，证明读的是主存储而非降级内存）
        Thread.sleep(250)
        fake.healthy = true
        fake.store["dg:key"] = "primary"
        assertEquals("primary", dao.get("dg:key"))
        assertEquals(3, fake.attempts.get())
    }

    @Test
    fun `memory fallback is instance-local (no cross-service semantics during outage)`() {
        val fake = FlakyPrimaryDao()
        val daoA = SaTokenOutageFallbackDao(fake, failureThreshold = 1, reopenMillis = 60_000)
        val daoB = SaTokenOutageFallbackDao(fake, failureThreshold = 1, reopenMillis = 60_000)

        daoA.setObject("dg:obj", mapOf("k" to "v"), 60)
        assertEquals(mapOf("k" to "v"), daoA.getObject("dg:obj"))
        // 实例 B 的内存兜底为空：降级语义即"token 不跨服务但功能正常"
        assertNotEquals(mapOf("k" to "v"), daoB.getObject("dg:obj"))
    }

    // ---------- fakes ----------

    /** 可切换健康状态的假主存储：故障时抛连接异常（降级路径的触发形态） */
    private class FlakyPrimaryDao : SaTokenDao {
        var healthy: Boolean = false
        val attempts = AtomicInteger(0)
        val store = HashMap<String, Any?>()

        private fun gate() {
            attempts.incrementAndGet()
            if (!healthy) {
                throw RedisConnectionFailureException("Unable to connect to Redis")
            }
        }

        override fun get(key: String): String? {
            gate()
            return store[key] as String?
        }

        override fun set(
            key: String,
            value: String,
            timeout: Long,
        ) {
            gate()
            store[key] = value
        }

        override fun update(
            key: String,
            value: String,
        ) {
            gate()
            store[key] = value
        }

        override fun delete(key: String) {
            gate()
            store.remove(key)
        }

        override fun getTimeout(key: String): Long {
            gate()
            return SaTokenDao.NOT_VALUE_EXPIRE
        }

        override fun updateTimeout(
            key: String,
            timeout: Long,
        ) {
            gate()
        }

        override fun getObject(key: String): Any? {
            gate()
            return store[key]
        }

        override fun <T> getObject(
            key: String,
            type: Class<T>,
        ): T {
            gate()
            @Suppress("UNCHECKED_CAST")
            return store[key] as T
        }

        override fun setObject(
            key: String,
            value: Any,
            timeout: Long,
        ) {
            gate()
            store[key] = value
        }

        override fun updateObject(
            key: String,
            value: Any,
        ) {
            gate()
            store[key] = value
        }

        override fun deleteObject(key: String) {
            gate()
            store.remove(key)
        }

        override fun getObjectTimeout(key: String): Long {
            gate()
            return SaTokenDao.NOT_VALUE_EXPIRE
        }

        override fun updateObjectTimeout(
            key: String,
            timeout: Long,
        ) {
            gate()
        }

        override fun getSession(key: String): SaSession? {
            gate()
            return store[key] as SaSession?
        }

        override fun setSession(
            session: SaSession,
            timeout: Long,
        ) {
            gate()
            store[session.id] = session
        }

        override fun updateSession(session: SaSession) {
            gate()
            store[session.id] = session
        }

        override fun deleteSession(key: String) {
            gate()
            store.remove(key)
        }

        override fun getSessionTimeout(key: String): Long {
            gate()
            return SaTokenDao.NOT_VALUE_EXPIRE
        }

        override fun updateSessionTimeout(
            key: String,
            timeout: Long,
        ) {
            gate()
        }

        override fun searchData(
            prefix: String,
            keyword: String,
            start: Int,
            size: Int,
            sortType: Boolean,
        ): List<String> {
            gate()
            return emptyList()
        }
    }
}
