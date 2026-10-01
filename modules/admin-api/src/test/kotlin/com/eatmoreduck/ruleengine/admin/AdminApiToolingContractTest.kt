package com.eatmoreduck.ruleengine.admin

import cn.dev33.satoken.filter.SaFirewallCheckFilterForJakartaServlet
import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet
import com.eatmoreduck.ruleengine.dsl.DslJson
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
import tools.jackson.databind.JsonNode
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * admin-api 工具面（第二批补齐接口）契约测试：测试执行 + 导入导出 + 缓存管理。
 *
 * 与 AdminApiInsightContractTest 同款设施：Testcontainers PG16（真实 PostgreSQL + Flyway
 * 迁移 + 权限种子）+ 完整 Spring 上下文（Sa-Token 拦截链）+ MockMvc；另加 Redis7 容器
 * （缓存清空端点经 pub/sub 广播失效事件，以订阅方身份断言频道载荷，口径同
 * CacheInvalidationPublishContractTest）。无 Docker 的环境自动跳过。
 *
 * 断言口径为前端消费方（frontend/src/api/analytics.ts 的 executeTest、importExport.ts、
 * types/analytics.ts 的 TestResult、types/importExport.ts 的 RuleExportData）。
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@SpringBootTest
@DisplayName("admin-api 契约：测试执行 + 导入导出 + 缓存管理")
class AdminApiToolingContractTest {
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
            registry.add("ruleengine.storage.url") {
                val url = postgres.jdbcUrl
                if (url.contains("?")) "$url&stringtype=unspecified" else "$url?stringtype=unspecified"
            }
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

    /** MockMvc 懒初始化（Sa-Token 上下文过滤器显式挂载，口径同 AdminApiContractTest） */
    private val mockMvc: MockMvc by lazy {
        val builder = MockMvcBuilders.webAppContextSetup(applicationContext)
        builder.addFilters<DefaultMockMvcBuilder>(
            SaFirewallCheckFilterForJakartaServlet(),
            SaTokenContextFilterForJakartaServlet(),
        )
        builder.build()
    }

    private val token: String by lazy { login() }

    /** 业务种子数据（规则经 API 创建；全部测试共享，只执行一次） */
    private val seeded: Boolean by lazy { seedData() }

    private val amountRule = "tool_amount_rule"
    private val boolRule = "tool_bool_rule"
    private val mapRule = "tool_map_rule"
    private val errorRule = "tool_error_rule"
    private val timeoutRule = "tool_timeout_rule"
    private val versionedRule = "tool_versioned_rule"
    private val disabledRule = "tool_disabled_rule"

    private val amountScript = "def amt = features.order_amount\nreturn amt > 1000 ? 'REJECT' : 'PASS'"
    private val versionedScriptV2 = "def amt = features.order_amount\nreturn amt > 200 ? 'REJECT' : 'PASS'"

    // ---------- 工具 ----------

    private fun login(
        username: String = "admin",
        password: String = "admin123",
    ): String =
        post(
            "/api/v1/auth/login",
            body = """{"username":"$username","password":"$password"}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()
            .response
            .contentAsString
            .let {
                DslJson.mapper
                    .readTree(it)
                    .get("token")
                    .asText()
            }

    private fun get(
        path: String,
        token: String? = null,
        operator: String? = null,
    ) = mockMvc.perform(
        MockMvcRequestBuilders.get(path).apply {
            token?.let { header("Authorization", it) }
            operator?.let { header("X-Operator", it) }
        },
    )

    private fun post(
        path: String,
        token: String? = null,
        body: String? = null,
        operator: String? = null,
    ) = mockMvc
        .perform(
            MockMvcRequestBuilders.post(path).apply {
                token?.let { header("Authorization", it) }
                operator?.let { header("X-Operator", it) }
                body?.let {
                    contentType("application/json")
                    content(it)
                }
            },
        )

    private fun put(
        path: String,
        token: String? = null,
        body: String? = null,
    ) = mockMvc
        .perform(
            MockMvcRequestBuilders.put(path).apply {
                token?.let { header("Authorization", it) }
                body?.let {
                    contentType("application/json")
                    content(it)
                }
            },
        )

    private fun json(raw: String): String = DslJson.mapper.writeValueAsString(raw)

    /** 读取 200 响应的 JSON 树 */
    private fun okBody(request: org.springframework.test.web.servlet.ResultActions): JsonNode {
        val body =
            request
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
                .response
                .contentAsString
        return DslJson.mapper.readTree(body)
    }

    /** 收一条广播事件（超时失败，附带已收事件便于排障） */
    private fun awaitEvent(description: String): CacheInvalidationEvent {
        val event = received.poll(5, TimeUnit.SECONDS)
        assertNotNull(event, "5s 内未收到广播事件($description); 已收到: $received")
        return event
    }

    // ---------- 种子数据 ----------

    /** 播种测试面规则（经 API 创建，覆盖各结果形态脚本 + 版本历史 + 禁用态） */
    private fun seedData(): Boolean {
        if (ruleExists(amountRule)) return true
        createRule(amountRule, "金额规则", amountScript)
        createRule(boolRule, "年龄规则", "return features.user_age >= 18")
        createRule(mapRule, "黑名单规则", "return ['decision': 'REJECT', 'reason': '命中黑名单']")
        createRule(errorRule, "异常规则", "throw new RuntimeException('tool-boom')")
        createRule(timeoutRule, "超时规则", "while (true) { }")
        createRule(versionedRule, "多版本规则", "return 'PASS'")
        put(
            "/api/v1/rules/$versionedRule",
            token,
            """{"groovyScript":${json(versionedScriptV2)},"changeReason":"导出测试第二版本"}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
        createRule(disabledRule, "禁用规则", "return 'PASS'")
        post("/api/v1/rules/$disabledRule/disable", token, "").andExpect(MockMvcResultMatchers.status().isOk)
        return true
    }

    private fun ruleExists(ruleKey: String): Boolean = get("/api/v1/rules/$ruleKey", token).andReturn().response.status == 200

    private fun createRule(
        ruleKey: String,
        ruleName: String,
        script: String,
    ) {
        post(
            "/api/v1/rules",
            token,
            """{"ruleKey":"$ruleKey","ruleName":"$ruleName","groovyScript":${json(script)}}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
    }

    /** 构造导入载荷的单条规则记录（对齐前端导出文件回传形态） */
    private fun importRecord(
        ruleKey: String,
        ruleName: String,
        script: String,
        enabled: Boolean = true,
    ): String =
        """{"rule":{"id":null,"ruleKey":"$ruleKey","ruleName":"$ruleName","ruleDescription":null,""" +
            """"groovyScript":${json(script)},"version":1,"createdBy":"exporter","createdAt":null,""" +
            """"updatedBy":null,"updatedAt":null,"enabled":$enabled,"deleted":false,"optLockVersion":0,""" +
            """"activeVersion":1,"environmentId":null,"teamId":null},"versions":[]}"""

    private fun importPayload(records: List<String>): String =
        """{"formatVersion":"1.0","exportedAt":"2026-10-01T00:00:00","exportedBy":"exporter","rules":[${records.joinToString(",")}]}"""

    // ---------- 鉴权 ----------

    @Test
    @Order(5)
    @DisplayName("未登录访问三组接口均返回 401 旧契约结构")
    fun unauthorizedHasLegacyErrorShape() {
        post("/api/v1/test/rules/$amountRule/execute", body = "{}")
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(401))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("未登录或登录已过期"))
        get("/api/v1/export/rules")
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        post("/api/v1/cache/evict-all")
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
    }

    // ---------- 测试执行 ----------

    @Test
    @Order(10)
    @DisplayName("字符串返回脚本：按测试参数得出决策并回显命中条件快照")
    fun stringResultRuleExecutesWithFeatures() {
        seeded
        val result =
            okBody(
                post("/api/v1/test/rules/$amountRule/execute", token, """{"order_amount":1500}"""),
            )
        assertEquals(amountRule, result.get("ruleKey").asText())
        assertTrue(result.get("success").asBoolean())
        assertEquals("REJECT", result.get("decision").asText())
        assertEquals("规则执行完成", result.get("reason").asText())
        assertTrue(result.get("executionTimeMs").asLong() >= 0)
        assertTrue(result.get("errorMessage").isNull)
        assertEquals(listOf("order_amount = 1500"), result.get("matchedConditions").toList().map { it.asText() })
        assertEquals(1500, result.get("executionContext").get("order_amount").asInt())

        // 反例参数走另一分支
        val pass =
            okBody(
                post("/api/v1/test/rules/$amountRule/execute", token, """{"order_amount":500}"""),
            )
        assertEquals("PASS", pass.get("decision").asText())
    }

    @Test
    @Order(11)
    @DisplayName("布尔返回脚本：true → PASS，false → REJECT")
    fun booleanResultRuleMapsDecision() {
        seeded
        val adult =
            okBody(
                post("/api/v1/test/rules/$boolRule/execute", token, """{"user_age":20}"""),
            )
        assertTrue(adult.get("success").asBoolean())
        assertEquals("PASS", adult.get("decision").asText())
        val minor =
            okBody(
                post("/api/v1/test/rules/$boolRule/execute", token, """{"user_age":-1}"""),
            )
        assertEquals("REJECT", minor.get("decision").asText())
    }

    @Test
    @Order(12)
    @DisplayName("Map 返回脚本：decision/reason 取自脚本返回值")
    fun mapResultRuleCarriesDecisionAndReason() {
        seeded
        val result =
            okBody(
                post("/api/v1/test/rules/$mapRule/execute", token, """{"user_id":"u-1"}"""),
            )
        assertTrue(result.get("success").asBoolean())
        assertEquals("REJECT", result.get("decision").asText())
        assertEquals("命中黑名单", result.get("reason").asText())
        assertEquals(listOf("user_id = u-1"), result.get("matchedConditions").toList().map { it.asText() })
    }

    @Test
    @Order(13)
    @DisplayName("脚本运行期异常：200 + 失败形态（errorMessage 前缀与旧契约一致）")
    fun runtimeFailureReturnsFailureShape() {
        seeded
        val result =
            okBody(
                post("/api/v1/test/rules/$errorRule/execute", token, "{}"),
            )
        assertEquals(errorRule, result.get("ruleKey").asText())
        assertTrue(!result.get("success").asBoolean())
        assertTrue(
            result.get("errorMessage").asText().startsWith("测试执行失败: "),
            "errorMessage 应以旧契约前缀开头，实际: ${result.get("errorMessage").asText()}",
        )
        assertTrue(result.get("errorMessage").asText().contains("tool-boom"))
        assertTrue(result.get("decision").isNull)
        assertTrue(result.get("matchedConditions").isNull)
        assertTrue(result.get("executionContext").isNull)
    }

    @Test
    @Order(14)
    @DisplayName("死循环脚本：沙箱超时中断并返回失败形态")
    fun timeoutScriptIsInterrupted() {
        seeded
        val result =
            okBody(
                post("/api/v1/test/rules/$timeoutRule/execute", token, "{}"),
            )
        assertTrue(!result.get("success").asBoolean())
        assertTrue(
            result.get("errorMessage").asText().contains("脚本执行超时"),
            "errorMessage 应说明超时，实际: ${result.get("errorMessage").asText()}",
        )
        // 引擎默认执行超时 5s：超时路径的耗时不应显著低于该阈值
        assertTrue(result.get("executionTimeMs").asLong() >= 4000)
    }

    @Test
    @Order(15)
    @DisplayName("规则不存在：200 + 失败形态（旧契约文案）")
    fun missingRuleReturnsFailure() {
        seeded
        val result =
            okBody(
                post("/api/v1/test/rules/tool_missing_rule/execute", token, "{}"),
            )
        assertEquals("tool_missing_rule", result.get("ruleKey").asText())
        assertTrue(!result.get("success").asBoolean())
        assertEquals("规则不存在: tool_missing_rule", result.get("errorMessage").asText())
        assertTrue(result.get("decision").isNull)
    }

    // ---------- 导入导出 ----------

    @Test
    @Order(20)
    @DisplayName("全量导出：规则主记录 + 版本历史 + 元信息（X-Operator 回填 exportedBy）")
    fun exportAllRulesPacksRecordsAndVersions() {
        seeded
        val data = okBody(get("/api/v1/export/rules", token, operator = "tool-operator"))
        assertEquals("1.0", data.get("formatVersion").asText())
        assertEquals("tool-operator", data.get("exportedBy").asText())
        assertTrue(data.get("exportedAt").asText().isNotEmpty())

        val records = data.get("rules").toList()
        assertTrue(records.size >= 7, "种子规则应全部导出，实际 ${records.size} 条")

        val amount = records.single { it.get("rule").get("ruleKey").asText() == amountRule }
        assertEquals(amountScript, amount.get("rule").get("groovyScript").asText())
        assertTrue(amount.get("rule").get("enabled").asBoolean())
        assertEquals(1, amount.get("versions").size())

        val versioned = records.single { it.get("rule").get("ruleKey").asText() == versionedRule }
        assertEquals(2, versioned.get("versions").size())
        // 版本历史降序：最新版本在前，主记录脚本为当前版本载荷
        assertEquals(2, versioned.get("versions")[0].get("version").asInt())
        assertEquals(versionedScriptV2, versioned.get("versions")[0].get("groovyScript").asText())
        assertEquals(versionedScriptV2, versioned.get("rule").get("groovyScript").asText())
        assertEquals("DRAFT", versioned.get("versions")[0].get("status").asText())

        // 禁用规则的 enabled 状态如实导出
        val disabled = records.single { it.get("rule").get("ruleKey").asText() == disabledRule }
        assertTrue(!disabled.get("rule").get("enabled").asBoolean())
    }

    @Test
    @Order(21)
    @DisplayName("单条导出返回单元素列表；不存在规则返回 400 旧契约文案")
    fun exportSingleRuleAndMissingRule() {
        seeded
        val data = okBody(get("/api/v1/export/rules/$amountRule", token))
        val records = data.get("rules").toList()
        assertEquals(1, records.size)
        assertEquals(amountRule, records[0].get("rule").get("ruleKey").asText())

        get("/api/v1/export/rules/tool_missing_rule", token)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(400))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("规则不存在: tool_missing_rule"))
    }

    @Test
    @Order(22)
    @DisplayName("批量导出：存在的规则打包，不存在的 ruleKey 静默跳过")
    fun batchExportSkipsUnknownKeys() {
        seeded
        val data =
            okBody(
                post(
                    "/api/v1/export/rules/batch",
                    token,
                    """["$amountRule","$boolRule","tool_missing_rule"]""",
                ),
            )
        val keys = data.get("rules").toList().map { it.get("rule").get("ruleKey").asText() }
        assertEquals(listOf(amountRule, boolRule), keys)
    }

    @Test
    @Order(23)
    @DisplayName("导入：新规则创建（过校验）、已存在跳过、非法脚本失败收集明细")
    fun importRulesCountsAndCreates() {
        seeded
        val response =
            okBody(
                post(
                    "/api/v1/import/rules",
                    token,
                    importPayload(
                        listOf(
                            importRecord("tool_imported_new", "导入新规则", "return 'PASS'"),
                            importRecord(amountRule, "金额规则", amountScript),
                            importRecord("tool_imported_bad", "坏脚本规则", "def broken( {"),
                        ),
                    ),
                    operator = "tool-importer",
                ),
            )
        assertTrue(response.get("success").asBoolean())
        assertEquals(1, response.get("importedCount").asInt())
        assertEquals(1, response.get("skippedCount").asInt())
        assertEquals(1, response.get("failedCount").asInt())
        val failure = response.get("failures")[0].asText()
        assertTrue(failure.startsWith("规则 tool_imported_bad 导入失败: "), "实际: $failure")
        assertEquals("导入完成: 成功 1, 跳过 1, 失败 1", response.get("message").asText())

        // 导入的新规则经创建链路落库（可查询、脚本往返一致）
        val created = okBody(get("/api/v1/rules/tool_imported_new", token))
        assertEquals("导入新规则", created.get("ruleName").asText())
        assertEquals("return 'PASS'", created.get("groovyScript").asText())
        assertEquals("tool-importer", created.get("createdBy").asText())
        assertTrue(created.get("enabled").asBoolean())
    }

    @Test
    @Order(24)
    @DisplayName("导入：禁用态规则还原为禁用；空载荷返回全零计数")
    fun importPreservesDisabledStateAndEmptyPayload() {
        seeded
        post(
            "/api/v1/import/rules",
            token,
            importPayload(listOf(importRecord("tool_imported_disabled", "导入禁用规则", "return 'PASS'", enabled = false))),
        ).andExpect(MockMvcResultMatchers.status().isOk)
        val created = okBody(get("/api/v1/rules/tool_imported_disabled", token))
        assertTrue(!created.get("enabled").asBoolean())

        val empty =
            okBody(
                post("/api/v1/import/rules", token, importPayload(emptyList())),
            )
        assertEquals(0, empty.get("importedCount").asInt())
        assertEquals(0, empty.get("skippedCount").asInt())
        assertEquals(0, empty.get("failedCount").asInt())
        assertEquals(0, empty.get("failures").size())
    }

    // ---------- 缓存管理 ----------

    @Test
    @Order(30)
    @DisplayName("订阅契约频道成功（Redis 可用，监听器容器直接启动）")
    fun startListener() {
        listenerContainer =
            RedisMessageListenerContainer().apply {
                setConnectionFactory(this@AdminApiToolingContractTest.connectionFactory)
                addMessageListener(
                    { message, _ ->
                        message.body?.toString(Charsets.UTF_8)?.let { raw ->
                            CacheInvalidationCodec.decodeOrNull(raw)?.let(received::add)
                        }
                    },
                    ChannelTopic(CacheInvalidationTopics.CACHE_INVALIDATE),
                )
            }
        listenerContainer!!.afterPropertiesSet()
        listenerContainer!!.start()
        assertTrue(listenerContainer!!.isRunning)
    }

    @Test
    @Order(31)
    @DisplayName("缓存统计返回编译缓存的旧字段集")
    fun cacheStatsExposeLegacyFields() {
        seeded
        get("/api/v1/cache/stats", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$['compiled-scripts'].hitRate").isNumber)
            .andExpect(MockMvcResultMatchers.jsonPath("$['compiled-scripts'].hitCount").isNumber)
            .andExpect(MockMvcResultMatchers.jsonPath("$['compiled-scripts'].missCount").isNumber)
            .andExpect(MockMvcResultMatchers.jsonPath("$['compiled-scripts'].evictionCount").isNumber)
            .andExpect(MockMvcResultMatchers.jsonPath("$['compiled-scripts'].size").isNumber)
    }

    @Test
    @Order(32)
    @DisplayName("清除指定规则缓存：广播 RULE 失效事件（key = ruleKey）")
    fun evictRuleBroadcastsRuleInvalidation() {
        seeded
        received.clear()
        post("/api/v1/cache/evict/$amountRule", token, "")
            .andExpect(MockMvcResultMatchers.status().isOk)
        val event = awaitEvent("清除规则缓存")
        assertEquals(CacheInvalidationType.RULE, event.type)
        assertEquals(amountRule, event.key)
        assertEquals("admin-api", event.source)
    }

    @Test
    @Order(33)
    @DisplayName("清除所有缓存：广播 RULE/GRAYSCALE 整层失效事件（key = null）")
    fun evictAllBroadcastsLayerWideInvalidations() {
        received.clear()
        post("/api/v1/cache/evict-all", token, "")
            .andExpect(MockMvcResultMatchers.status().isOk)
        val events = listOf(awaitEvent("清除所有缓存-1"), awaitEvent("清除所有缓存-2"))
        assertEquals(
            setOf(CacheInvalidationType.RULE, CacheInvalidationType.GRAYSCALE),
            events.map { it.type }.toSet(),
        )
        assertTrue(events.all { it.key == null })
    }

    @Test
    @Order(34)
    @DisplayName("缓存预热端点返回 200（空操作，与旧实现一致）")
    fun warmUpIsNoOp() {
        post("/api/v1/cache/warm-up", token, "")
            .andExpect(MockMvcResultMatchers.status().isOk)
    }
}
