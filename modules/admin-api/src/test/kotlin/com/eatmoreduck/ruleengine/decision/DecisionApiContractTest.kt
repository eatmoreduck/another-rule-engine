package com.eatmoreduck.ruleengine.decision

import com.eatmoreduck.ruleengine.dsl.DslJson
import com.eatmoreduck.ruleengine.storage.log.CanaryExecutionLogTable
import com.eatmoreduck.ruleengine.storage.log.ExecutionLogsTable
import com.eatmoreduck.ruleengine.storage.table.DecisionFlowsTable
import com.eatmoreduck.ruleengine.storage.table.GrayscaleConfigsTable
import com.eatmoreduck.ruleengine.storage.table.RuleVersionsTable
import com.eatmoreduck.ruleengine.storage.table.RulesTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * decision-api 全链路契约测试：Testcontainers PG16（真实 PostgreSQL + Flyway V1__init 初始化脚本 +
 * 权限种子数据）+ 完整 Spring 上下文 + MockMvc。
 *
 * 断言口径为旧 DecisionController / AsyncDecisionController 的请求响应字段与 fail-safe 行为，
 * 以及执行日志 / 灰度执行日志 / 灰度指标的落库链路。
 * 无 Docker 的环境自动跳过整个类（等价 @Disabled，构建不挂）。
 *
 * 认证说明：sa-token.auth-enabled=false 整体关闭登录拦截（SaInterceptor 不装配，
 * @SaCheckPermission 注解随之不解析）；401 契约结构由 [DecisionApiAuthContractTest] 单独覆盖。
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@SpringBootTest(
    classes = [com.eatmoreduck.ruleengine.admin.AdminApiApplication::class],
    properties = ["sa-token.auth-enabled=false"],
)
@DisplayName("decision-api 契约：同步决策 + 灰度分流 + 决策流 + fail-safe + 异步轮询 + 日志落库")
class DecisionApiContractTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("ruleengine")
                .withUsername("test")
                .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            // JSONB 列文本写入依赖 stringtype=unspecified（见 ExecutionLogTables 注释）
            registry.add("ruleengine.storage.url") { withStringTypeUnspecified(postgres.jdbcUrl) }
            registry.add("ruleengine.storage.username", postgres::getUsername)
            registry.add("ruleengine.storage.password", postgres::getPassword)
            // 日志异步刷盘在测试中加速（默认 500ms，测试 100ms 便于轮询断言）
            registry.add("ruleengine.decision.log-flush-interval-ms") { "100" }
            registry.add("ruleengine.decision.log-batch-size") { "50" }
        }

        private fun withStringTypeUnspecified(url: String): String =
            if (url.contains("?")) "$url&stringtype=unspecified" else "$url?stringtype=unspecified"
    }

    @Autowired
    lateinit var applicationContext: WebApplicationContext

    private val mockMvc: MockMvc by lazy {
        MockMvcBuilders.webAppContextSetup(applicationContext).build()
    }

    // ---------- 工具 ----------

    /**
     * suspend controller 的请求封装：MockMvc 首次 perform 启动异步请求（协程执行），
     * 完成 asyncDispatch 拿到最终响应；非异步（拦截器直接拒绝等）原样返回。
     */
    private fun performAsync(
        requestBuilder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder,
    ): org.springframework.test.web.servlet.ResultActions {
        val initial = mockMvc.perform(requestBuilder)
        return if (initial.andReturn().request.isAsyncStarted) {
            mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(initial.andReturn()))
        } else {
            initial
        }
    }

    private fun post(
        path: String,
        body: String,
    ): org.springframework.test.web.servlet.ResultActions =
        performAsync(
            MockMvcRequestBuilders
                .post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )

    private fun get(path: String): org.springframework.test.web.servlet.ResultActions = performAsync(MockMvcRequestBuilders.get(path))

    /** 等待异步日志刷盘后查询（轮询 DB 至条件满足或超时） */
    private fun <T> awaitDbSlice(
        timeoutSeconds: Long = 5,
        query: () -> T,
    ): T {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        var last: T? = null
        while (System.nanoTime() < deadline) {
            last = transaction { query() }
            when (last) {
                is Int -> if (last > 0) return last
                is Long -> if (last > 0) return last
                is List<*> -> if (last.isNotEmpty()) return last
            }
            Thread.sleep(100)
        }
        return last ?: throw AssertionError("等待数据库条件超时")
    }

    // ---------- 种子数据 ----------

    private fun seedRule(
        ruleKey: String,
        activeScript: String,
        canaryScript: String? = null,
    ): Long {
        val ruleId =
            transaction {
                RulesTable.insert { statement ->
                    statement[RulesTable.ruleKey] = ruleKey
                    statement[ruleName] = "契约规则-$ruleKey"
                    statement[groovyScript] = "" // 新模型定义载荷收敛到 rule_versions，主表列恒占位
                    statement[version] = if (canaryScript != null) 2 else 1
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
            if (canaryScript != null) {
                RuleVersionsTable.insert { statement ->
                    statement[RuleVersionsTable.ruleId] = ruleId
                    statement[RuleVersionsTable.ruleKey] = ruleKey
                    statement[RuleVersionsTable.version] = 2
                    statement[RuleVersionsTable.groovyScript] = canaryScript
                    statement[RuleVersionsTable.changedBy] = "contract-test"
                    statement[RuleVersionsTable.changedAt] = Instant.now()
                    statement[RuleVersionsTable.status] = "CANARY"
                }
            }
        }
        return ruleId
    }

    private fun seedRunningGrayscale(
        ruleKey: String,
        currentVersion: Int,
        grayscaleVersion: Int,
        percentage: Int,
    ): Long {
        val configId =
            transaction {
                GrayscaleConfigsTable.insert { statement ->
                    statement[GrayscaleConfigsTable.ruleKey] = ruleKey
                    statement[GrayscaleConfigsTable.currentVersion] = currentVersion
                    statement[GrayscaleConfigsTable.grayscaleVersion] = grayscaleVersion
                    statement[grayscalePercentage] = percentage
                    statement[status] = "RUNNING"
                    statement[targetType] = "RULE"
                    statement[targetKey] = ruleKey
                    statement[strategyType] = "PERCENTAGE"
                    statement[startedAt] = Instant.now()
                    statement[createdBy] = "contract-test"
                    statement[createdAt] = Instant.now()
                } get GrayscaleConfigsTable.id
            }
        // 模拟 admin-api 创建灰度时的零值指标行（旧 initMetrics；决策侧原子 UPDATE 的目标行）
        seedMetricRow(configId, currentVersion)
        seedMetricRow(configId, grayscaleVersion)
        return configId
    }

    private fun seedMetricRow(
        configId: Long,
        version: Int,
    ) {
        transaction {
            GrayscaleMetricsSeedTable.insert { statement ->
                statement[configIdValue] = configId
                statement[versionValue] = version
                statement[executionCountValue] = 0
                statement[hitCountValue] = 0
                statement[errorCountValue] = 0
                statement[avgExecutionTimeMsValue] = 0
            }
        }
    }

    /** grayscale_metrics 零值行种子（列与 V6 基线一致） */
    private object GrayscaleMetricsSeedTable : org.jetbrains.exposed.v1.core.Table("grayscale_metrics") {
        val id = long("id").autoIncrement()
        val configIdValue = long("grayscale_config_id")
        val versionValue = integer("version")
        val executionCountValue = integer("execution_count")
        val hitCountValue = integer("hit_count")
        val errorCountValue = integer("error_count")
        val avgExecutionTimeMsValue = integer("avg_execution_time_ms")

        override val primaryKey = PrimaryKey(id)
    }

    private fun seedFlow(
        flowKey: String,
        graphJson: String,
    ) {
        transaction {
            DecisionFlowsTable.insert { statement ->
                statement[DecisionFlowsTable.flowKey] = flowKey
                statement[flowName] = "契约流-$flowKey"
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

    // ---------- 契约用例 ----------

    @Test
    @Order(1)
    @DisplayName("GET /api/v1/health 返回 OK")
    fun health() {
        get("/api/v1/health")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().string("OK"))
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/v1/decide 直传脚本：字符串输出与契约字段（含 executionContext 回显）")
    fun adHocDecision() {
        val body =
            """{"ruleId":"adhoc-1","script":"return features.order_amount > 1000 ? 'REJECT' : 'PASS'","features":{"order_amount":100,"userId":"u-1"},"timeoutMs":50}"""
        post("/api/v1/decide", body)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.decision").value("PASS"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.reason").value("规则执行完成"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.executionTimeMs").isNumber)
            .andExpect(MockMvcResultMatchers.jsonPath("$.timeout").value(false))
            .andExpect(MockMvcResultMatchers.jsonPath("$.executionContext.order_amount").value(100))

        val rejectBody =
            """{"ruleId":"adhoc-2","script":"return features.order_amount > 1000 ? 'REJECT' : 'PASS'","features":{"order_amount":5000},"timeoutMs":50}"""
        post("/api/v1/decide", rejectBody)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.decision").value("REJECT"))
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/v1/decide 直传脚本：Map 输出与非法输出 fail-safe")
    fun adHocDecisionPayloadVariants() {
        val mapOutput =
            """{"ruleId":"adhoc-3","script":"return [decision: 'MANUAL_REVIEW', reason: '疑似盗刷']","features":{},"timeoutMs":50}"""
        post("/api/v1/decide", mapOutput)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.decision").value("MANUAL_REVIEW"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.reason").value("疑似盗刷"))

        // 编译失败 → fail-safe REJECT（200 响应体，非 5xx）
        val broken =
            """{"ruleId":"adhoc-4","script":"System.exit(1)","features":{},"timeoutMs":50}"""
        post("/api/v1/decide", broken)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.decision").value("REJECT"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.timeout").value(false))
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/v1/decide/{ruleKey} cache-aware：走生效版本脚本")
    fun decideByKey() {
        seedRule(
            "contract_rule_active",
            activeScript = "return features.order_amount > 10000 ? 'REJECT' : 'PASS'",
        )
        post("/api/v1/decide/contract_rule_active", """{"order_amount":500,"userId":"u-2"}""")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.decision").value("PASS"))

        // 不存在的规则 → fail-safe REJECT（旧文案逐字）
        post("/api/v1/decide/no_such_rule", "{}")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.decision").value("REJECT"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.reason").value("规则不存在或未启用"))
    }

    @Test
    @Order(5)
    @DisplayName("请求校验失败 → 400 旧契约三字段结构")
    fun validationError() {
        post("/api/v1/decide", """{"ruleId":"","script":null,"timeoutMs":50}""")
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(400))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").isNotEmpty)
            .andExpect(MockMvcResultMatchers.jsonPath("$.error").value("Bad Request"))
    }

    @Test
    @Order(6)
    @DisplayName("灰度分流：100% 命中灰度版本（阈值收紧后 REJECT）+ canary 日志 + 灰度指标")
    fun grayscaleFullSplit() {
        seedRule(
            "contract_rule_canary100",
            activeScript = "return features.order_amount > 10000 ? 'REJECT' : 'PASS'",
            canaryScript = "return features.order_amount > 100 ? 'REJECT' : 'PASS'",
        )
        val configId = seedRunningGrayscale("contract_rule_canary100", currentVersion = 1, grayscaleVersion = 2, percentage = 100)

        post("/api/v1/decide/contract_rule_canary100", """{"order_amount":500,"userId":"u-canary"}""")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.decision").value("REJECT"))

        // canary_execution_log 落库：is_canary=true、version_used=2
        val canaryRows =
            awaitDbSlice {
                CanaryExecutionLogTable
                    .selectAll()
                    .where {
                        (CanaryExecutionLogTable.targetKey eq "contract_rule_canary100") and
                            (CanaryExecutionLogTable.isCanary eq true)
                    }.count()
            }
        org.junit.jupiter.api.Assertions
            .assertTrue(canaryRows is Long && canaryRows > 0, "灰度执行日志应落库")

        // grayscale_metrics：execution_count 与 hit_count 同步递增（命中灰度版本）
        awaitDbSlice {
            GrayscaleMetricProbe.countHits(configId, 2)
        }
        val (execCount, hitCount) =
            transaction {
                val row =
                    GrayscaleMetricProbe
                        .row(configId, 2)
                requireNotNull(row)
            }
        org.junit.jupiter.api.Assertions
            .assertTrue(execCount >= 1, "execution_count 应递增")
        org.junit.jupiter.api.Assertions
            .assertTrue(hitCount >= 1, "hit_count 应递增")
    }

    @Test
    @Order(7)
    @DisplayName("灰度分流：0% 不命中（走当前版本 PASS），metrics 记执行不计命中")
    fun grayscaleZeroSplit() {
        seedRule(
            "contract_rule_canary0",
            activeScript = "return features.order_amount > 10000 ? 'REJECT' : 'PASS'",
            canaryScript = "return features.order_amount > 100 ? 'REJECT' : 'PASS'",
        )
        val configId = seedRunningGrayscale("contract_rule_canary0", currentVersion = 1, grayscaleVersion = 2, percentage = 0)

        post("/api/v1/decide/contract_rule_canary0", """{"order_amount":500,"userId":"u-stable"}""")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.decision").value("PASS"))

        awaitDbSlice { GrayscaleMetricProbe.countExecutions(configId, 1) }
        val (execCount, hitCount) = transaction { requireNotNull(GrayscaleMetricProbe.row(configId, 1)) }
        org.junit.jupiter.api.Assertions
            .assertTrue(execCount >= 1, "未命中灰度仍应记执行次数（当前版本行）")
        // 旧 JPQL 口径：isSuccess=true 时该版本行 hit_count 同步 +1（版本维度的"成功执行计数"），0% 分流不特殊处理
        org.junit.jupiter.api.Assertions
            .assertEquals(execCount, hitCount, "hit_count 应与成功执行数一致（旧口径）")
    }

    @Test
    @Order(8)
    @DisplayName("决策流：condition 双分支（金额超限 REJECT / 正常 PASS）")
    fun decisionFlowExecution() {
        seedFlow(
            "contract_flow",
            """
            {"nodes":[
              {"id":"s","type":"start","data":{"label":"开始","nodeType":"start"}},
              {"id":"c","type":"condition","data":{"label":"金额","nodeType":"condition","fieldName":"order_amount","operator":"GT","threshold":500}},
              {"id":"r","type":"action","data":{"label":"拒绝","nodeType":"action","action":"REJECT","reason":"金额超限"}},
              {"id":"e","type":"end","data":{"label":"结束","nodeType":"end","defaultAction":"PASS","defaultReason":"默认放行"}}
            ],"edges":[
              {"id":"e1","source":"s","target":"c"},
              {"id":"e2","source":"c","target":"r","sourceHandle":"true"},
              {"id":"e3","source":"c","target":"e","sourceHandle":"false"}
            ]}
            """.trimIndent(),
        )
        post("/api/v1/decision-flows/contract_flow/execute", """{"order_amount":1000}""")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.decision").value("REJECT"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.reason").value("金额超限"))

        post("/api/v1/decision-flows/contract_flow/execute", """{"order_amount":100}""")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.decision").value("PASS"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.reason").value("默认放行"))

        // 不存在的流 → fail-safe（旧文案逐字）
        post("/api/v1/decision-flows/no_such_flow/execute", "{}")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.decision").value("REJECT"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.reason").value("决策流不存在或未启用"))
    }

    @Test
    @Order(9)
    @DisplayName("fail-safe 超时：死循环脚本 → REJECT + timeout=true")
    fun executionTimeoutFailsSafe() {
        val body =
            """{"ruleId":"adhoc-timeout","script":"def i = 0\nwhile (true) { i++ }\nreturn 'PASS'","features":{},"timeoutMs":50}"""
        post("/api/v1/decide", body)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.decision").value("REJECT"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.timeout").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.reason").value("规则执行超时"))
    }

    @Test
    @Order(10)
    @DisplayName("异步决策：提交 202 PROCESSING → 轮询 COMPLETED 契约字段")
    fun asyncSubmitAndPoll() {
        val submitBody =
            """{"ruleId":"async-rule","script":"return features.order_amount > 1000 ? 'REJECT' : 'PASS'","features":{"order_amount":100},"timeoutMs":200}"""
        val response =
            post("/api/v1/decide/async", submitBody)
                .andExpect(MockMvcResultMatchers.status().isAccepted)
                .andExpect(MockMvcResultMatchers.jsonPath("$.requestId").isNotEmpty)
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("PROCESSING"))
                .andReturn()
                .response
                .contentAsString
        val requestId =
            DslJson
                .mapper
                .readTree(response)
                .get("requestId")
                .asText()

        // 轮询至完成（后台协程执行，本地毫秒级）
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var completed = false
        while (System.nanoTime() < deadline && !completed) {
            val poll =
                get("/api/v1/decide/async/$requestId")
                    .andExpect(MockMvcResultMatchers.status().isOk)
                    .andReturn()
                    .response
                    .contentAsString
            val json = DslJson.mapper.readTree(poll)
            if ("COMPLETED" == json.get("status").asText()) {
                completed = true
                org.junit.jupiter.api.Assertions
                    .assertEquals("PASS", json.get("decision").asText())
                org.junit.jupiter.api.Assertions
                    .assertFalse(json.get("timeout").asBoolean())
                org.junit.jupiter.api.Assertions
                    .assertTrue(json.get("executionTimeMs").isNumber)
            } else {
                Thread.sleep(50)
            }
        }
        org.junit.jupiter.api.Assertions
            .assertTrue(completed, "异步决策应在 10s 内完成")
    }

    @Test
    @Order(11)
    @DisplayName("执行日志落库：成功与超时记录（异步批量刷盘）")
    fun executionLogPersisted() {
        // 前序用例已产生 SUCCESS（order 2/4）与 TIMEOUT（order 9）记录，等待刷盘后断言
        val successRows =
            awaitDbSlice {
                ExecutionLogsTable
                    .selectAll()
                    .where { ExecutionLogsTable.status eq "SUCCESS" }
                    .count()
            }
        org.junit.jupiter.api.Assertions
            .assertTrue(successRows is Long && successRows > 0, "SUCCESS 执行日志应落库")

        val timeoutRows =
            awaitDbSlice {
                ExecutionLogsTable
                    .selectAll()
                    .where { ExecutionLogsTable.status eq "TIMEOUT" }
                    .count()
            }
        org.junit.jupiter.api.Assertions
            .assertTrue(timeoutRows is Long && timeoutRows > 0, "TIMEOUT 执行日志应落库")

        val logged =
            transaction {
                ExecutionLogsTable
                    .selectAll()
                    .where { ExecutionLogsTable.status eq "SUCCESS" }
                    .first()
            }
        org.junit.jupiter.api.Assertions
            .assertNotNull(logged[ExecutionLogsTable.ruleKey])
        org.junit.jupiter.api.Assertions
            .assertNotNull(logged[ExecutionLogsTable.outputDecision])
    }

    /** grayscale_metrics 探针（仅契约测试用的最小列读取） */
    private object GrayscaleMetricProbe {
        fun row(
            configId: Long,
            version: Int,
        ): Pair<Int, Int>? =
            GrayscaleMetricTableInternal
                .selectAll()
                .where {
                    (GrayscaleMetricTableInternal.configId eq configId) and (GrayscaleMetricTableInternal.version eq version)
                }.singleOrNull()
                ?.let { (it[GrayscaleMetricTableInternal.executionCount] ?: 0) to (it[GrayscaleMetricTableInternal.hitCount] ?: 0) }

        fun countExecutions(
            configId: Long,
            version: Int,
        ): Int = row(configId, version)?.first ?: 0

        fun countHits(
            configId: Long,
            version: Int,
        ): Int = row(configId, version)?.second ?: 0

        private object GrayscaleMetricTableInternal : org.jetbrains.exposed.v1.core.Table("grayscale_metrics") {
            val configId = long("grayscale_config_id")
            val version = integer("version")
            val executionCount = integer("execution_count")
            val hitCount = integer("hit_count")

            override val primaryKey = PrimaryKey(configId, version)
        }
    }
}
