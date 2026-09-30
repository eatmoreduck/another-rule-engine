package com.example.ruleengine.decision

import cn.dev33.satoken.dao.SaTokenDao
import cn.dev33.satoken.session.SaSession
import com.example.ruleengine.decision.core.FeatureResolutionService
import com.example.ruleengine.decision.core.GrayscaleRouter
import com.example.ruleengine.decision.core.RuleSnapshotOutcome
import com.example.ruleengine.decision.repo.NameListLookup
import com.example.ruleengine.shared.cache.CacheInvalidationCodec
import com.example.ruleengine.shared.cache.CacheInvalidationEvent
import com.example.ruleengine.shared.cache.CacheInvalidationTopics
import com.example.ruleengine.shared.cache.CacheInvalidationType
import com.example.ruleengine.shared.satoken.SaTokenOutageFallbackDao
import com.example.ruleengine.storage.table.DecisionFlowsTable
import com.example.ruleengine.storage.table.GrayscaleConfigsTable
import com.example.ruleengine.storage.table.RuleVersionsTable
import com.example.ruleengine.storage.table.RulesTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 缓存失效广播契约测试（Testcontainers PG16 + Redis7，完整上下文）：
 * admin-api 侧的发布端点等价物（convertAndSend 到契约频道）→ decision 订阅侧按事件类别
 * 失效对应 Caffeine 缓存层，并验证失效后重查读到新数据（而非 TTL 过期兜底）。
 *
 * 同时覆盖决策侧 Sa-Token 会话的 Redis 主存储装配（@Primary 包装器 + 官方 dao）。
 * 无 Docker 环境自动跳过整个类。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = ["sa-token.auth-enabled=false"])
@DisplayName("阶段 5：缓存失效广播——admin 变更 → decision 缓存即时失效")
class CacheInvalidationBroadcastTest {
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
        fun properties(registry: DynamicPropertyRegistry) {
            val url = postgres.jdbcUrl
            registry.add("ruleengine.storage.url") {
                if (url.contains("?")) "$url&stringtype=unspecified" else "$url?stringtype=unspecified"
            }
            registry.add("ruleengine.storage.username", postgres::getUsername)
            registry.add("ruleengine.storage.password", postgres::getPassword)
            registry.add("spring.data.redis.url") { "redis://${redis.host}:${redis.getMappedPort(6379)}" }
        }

        private val SEEDED =
            java.util.concurrent.atomic
                .AtomicBoolean(false)

        /** 等待订阅侧生效（轮询断言条件，超时抛错） */
        fun await(
            timeoutSeconds: Long = 5,
            condition: () -> Boolean,
        ) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
            while (System.nanoTime() < deadline) {
                if (condition()) return
                Thread.sleep(50)
            }
            throw AssertionError("等待失效广播生效超时（${timeoutSeconds}s）")
        }

        private fun seedRule(
            ruleKey: String,
            activeScript: String,
        ) {
            val ruleId =
                transaction {
                    RulesTable.insert { statement ->
                        statement[RulesTable.ruleKey] = ruleKey
                        statement[ruleName] = "广播契约-$ruleKey"
                        statement[groovyScript] = ""
                        statement[version] = 2
                        statement[activeVersion] = 1
                        statement[enabled] = true
                        statement[deleted] = false
                        statement[createdBy] = "contract-test"
                        statement[createdAt] = Instant.now()
                    } get RulesTable.id
                }
            transaction {
                RuleVersionsTable.insert { statement ->
                    statement[RuleVersionsTable.ruleId] = ruleId
                    statement[RuleVersionsTable.ruleKey] = ruleKey
                    statement[RuleVersionsTable.version] = 1
                    statement[RuleVersionsTable.groovyScript] = activeScript
                    statement[RuleVersionsTable.changedBy] = "contract-test"
                    statement[RuleVersionsTable.changedAt] = Instant.now()
                    statement[RuleVersionsTable.status] = "ACTIVE"
                }
                // 灰度候选版本（领域不变式：灰度版本不得与现行版本相同）
                RuleVersionsTable.insert { statement ->
                    statement[RuleVersionsTable.ruleId] = ruleId
                    statement[RuleVersionsTable.ruleKey] = ruleKey
                    statement[RuleVersionsTable.version] = 2
                    statement[RuleVersionsTable.groovyScript] = "return 'CANARY-v2'"
                    statement[RuleVersionsTable.changedBy] = "contract-test"
                    statement[RuleVersionsTable.changedAt] = Instant.now()
                    statement[RuleVersionsTable.status] = "CANARY"
                }
            }
        }

        private fun seedRunningGrayscale(ruleKey: String) {
            transaction {
                GrayscaleConfigsTable.insert { statement ->
                    statement[GrayscaleConfigsTable.ruleKey] = ruleKey
                    statement[currentVersion] = 1
                    statement[grayscaleVersion] = 2
                    statement[grayscalePercentage] = 100
                    statement[status] = "RUNNING"
                    statement[targetType] = "RULE"
                    statement[targetKey] = ruleKey
                    statement[strategyType] = "PERCENTAGE"
                    statement[startedAt] = Instant.now()
                    statement[createdBy] = "contract-test"
                    statement[createdAt] = Instant.now()
                }
            }
        }

        private fun seedFlow(
            flowKey: String,
            graphJson: String,
        ) {
            transaction {
                DecisionFlowsTable.insert { statement ->
                    statement[DecisionFlowsTable.flowKey] = flowKey
                    statement[flowName] = "广播契约流-$flowKey"
                    statement[flowGraph] = graphJson
                    statement[version] = 1
                    statement[activeVersion] = 1
                    statement[status] = "ACTIVE"
                    statement[createdBy] = "contract-test"
                    statement[createdAt] = Instant.now()
                    statement[enabled] = true
                }
            }
        }
    }

    @Autowired
    lateinit var router: GrayscaleRouter

    @Autowired
    lateinit var featureResolutionService: FeatureResolutionService

    @Autowired
    lateinit var nameListLookup: NameListLookup

    @Autowired
    lateinit var saTokenDao: SaTokenDao

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    private fun publish(
        type: CacheInvalidationType,
        key: String?,
    ) {
        // 等价 admin-api 的 CacheInvalidationForwarder 行为（事务提交后 convertAndSend）
        redisTemplate.convertAndSend(
            CacheInvalidationTopics.CACHE_INVALIDATE,
            CacheInvalidationCodec.encode(CacheInvalidationEvent.now(type, key, "contract-test")),
        )
    }

    /** 种子数据标记（companion 静态：JUnit 默认每用例新建实例，实例标记会重复播种） */

    private fun ensureSeeded() {
        if (SEEDED.compareAndSet(false, true)) {
            seedRule("bc_rule", "return 'PASS-v1'")
            seedRule("bc_rule_plain", "return 'PLAIN-v1'")
            seedRunningGrayscale("bc_rule")
            seedFlow("bc_flow", """{"nodes":[],"edges":[]}""")
        }
    }

    // ---------- 契约用例 ----------

    @Test
    @DisplayName("RULE 事件失效规则主行+版本载荷，重查读到新数据")
    fun `rule invalidation clears rule caches and next resolve reads fresh payload`() {
        ensureSeeded()
        // 1. 预热缓存（bc_rule_plain 无灰度配置，载荷断言不受灰度分流干扰）
        val first = router.resolveRule("bc_rule_plain", emptyMap())
        val firstSnapshot = assertIs<RuleSnapshotOutcome.Resolved>(first)
        assertTrue(firstSnapshot.snapshot.definitionJson.contains("PLAIN-v1"))
        await { router.cacheEntryCounts()["ruleMain"]!! >= 1 }

        // 2. DB 变更（admin 侧成功分支的等价物）+ 失效广播
        transaction {
            RuleVersionsTable.update({ (RuleVersionsTable.ruleKey eq "bc_rule_plain") and (RuleVersionsTable.version eq 1) }) {
                it[RuleVersionsTable.groovyScript] = "return 'PLAIN-v2'"
            }
        }
        publish(CacheInvalidationType.RULE, "bc_rule_plain")

        // 3. 行为断言：旧载荷 (PLAIN-v1) 持续命中缓存，广播失效后的首次重查才读到新载荷
        //    （轮询期间缓存未失效时恒返回 v1——该断言对"失效已发生"是充分条件，非 TTL 兜底：
        //    本缓存层 TTL 为 30s，3s 内翻转只能来自广播）
        await(timeoutSeconds = 3) {
            val resolved = router.resolveRule("bc_rule_plain", emptyMap())
            resolved is RuleSnapshotOutcome.Resolved && resolved.snapshot.definitionJson.contains("PLAIN-v2")
        }
    }

    @Test
    @DisplayName("GRAYSCALE 事件失效运行中灰度配置缓存（停止灰度后分流立即消失，非 TTL 兜底）")
    fun `grayscale invalidation stops canary routing immediately`() {
        ensureSeeded()
        val first = router.resolveRule("bc_rule", emptyMap())
        // 种子灰度 100% 分流：解析钉住灰度版本 2 且携带配置 id
        val firstSnapshot = assertIs<RuleSnapshotOutcome.Resolved>(first)
        assertTrue(firstSnapshot.snapshot.fromCanary, "种子灰度 100% 应命中灰度版本")
        val configId = firstSnapshot.snapshot.grayscaleConfigId

        // admin 侧停止灰度的等价物：配置离开 RUNNING
        transaction {
            GrayscaleConfigsTable.update({ GrayscaleConfigsTable.targetKey eq "bc_rule" }) {
                it[status] = "PAUSED"
            }
        }
        publish(CacheInvalidationType.GRAYSCALE, "bc_rule")

        // 广播失效前的重查仍命中灰度（缓存未失效）；失效后立即回退生效版本
        // （灰度缓存 TTL 为 10s，3s 内翻转只能来自广播）
        await(timeoutSeconds = 3) {
            val resolved = router.resolveRule("bc_rule", emptyMap())
            resolved is RuleSnapshotOutcome.Resolved &&
                !resolved.snapshot.fromCanary &&
                resolved.snapshot.grayscaleConfigId != configId
        }
    }

    @Test
    @DisplayName("FLOW 事件失效决策流主行+流图载荷")
    fun `flow invalidation clears flow caches`() {
        ensureSeeded()
        val outcome = router.resolveFlow("bc_flow", emptyMap())
        assertIs<com.example.ruleengine.decision.core.FlowSnapshotOutcome.Resolved>(outcome)
        await { router.cacheEntryCounts()["flowMain"]!! >= 1 }

        publish(CacheInvalidationType.FLOW, "bc_flow")
        await { router.cacheEntryCounts()["flowMain"] == 0L }
    }

    @Test
    @DisplayName("NAME_LIST 事件失效名单存在性缓存（负缓存命中后再查读到新增行）")
    fun `name list invalidation clears existence cache`() {
        ensureSeeded()
        assertTrue(!nameListLookup.existsActive("GLOBAL", "BLACK", "IP", "10.255.255.254"))

        // 广播前插入名单行（admin 名单新增的等价物）
        transaction {
            NameListSeedTable.insert { statement ->
                statement[listKey] = "GLOBAL"
                statement[listType] = "BLACK"
                statement[keyType] = "IP"
                statement[keyValue] = "10.255.255.254"
            }
        }
        publish(CacheInvalidationType.NAME_LIST, "GLOBAL")

        // 失效后重查命中新行（名单缓存 TTL 为 5s，2s 内变 true 证明走的是失效而非过期）
        await(timeoutSeconds = 3) { nameListLookup.existsActive("GLOBAL", "BLACK", "IP", "10.255.255.254") }
    }

    @Test
    @DisplayName("FEATURE 事件整层失效特征解析缓存")
    fun `feature invalidation clears feature caches`() {
        ensureSeeded()
        runBlocking {
            featureResolutionService.resolve(mapOf("bc_feature_code" to 1), emptyList(), 10)
        }
        await { featureResolutionService.cacheEntryCounts()["canonicalCode"]!! >= 1 }

        publish(CacheInvalidationType.FEATURE, null)
        await {
            featureResolutionService.cacheEntryCounts().values.all { it == 0L }
        }
    }

    @Test
    @DisplayName("决策侧 Sa-Token 会话落 Redis（@Primary 包装器注入 SaManager，键对其它实例可见）")
    fun `sa-token session lands in redis`() {
        // 决策侧注入的 dao 应为降级包装器（Redis 正常时走官方 Redis dao 主存储）
        assertIs<SaTokenOutageFallbackDao>(saTokenDao)

        val session =
            SaSession("satoken:login:session:5566")
                .setLoginId(5566L)
                .setToken("bc-token-5566")
        saTokenDao.setSession(session, 60)

        // 键真实落 Redis（同一库即 admin/decision 双侧可见，跨服务共享的数据基础）
        assertTrue(redisTemplate.hasKey("satoken:login:session:5566"))
        assertEquals(5566L, saTokenDao.getSession("satoken:login:session:5566")?.loginId)
    }

    // ---------- 种子 ----------

    /** name_list 最小种子表（列与 V11/V12/V13 基线一致；决策侧仅消费存在性） */
    private object NameListSeedTable : org.jetbrains.exposed.v1.core.Table("name_list") {
        val id = long("id").autoIncrement()
        val listKey = varchar("list_key", 255)
        val listType = varchar("list_type", 10)
        val keyType = varchar("key_type", 20)
        val keyValue = varchar("key_value", 256)

        override val primaryKey = PrimaryKey(id)
    }
}
