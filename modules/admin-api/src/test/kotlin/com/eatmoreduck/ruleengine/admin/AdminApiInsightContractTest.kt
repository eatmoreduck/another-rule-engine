package com.eatmoreduck.ruleengine.admin

import cn.dev33.satoken.filter.SaFirewallCheckFilterForJakartaServlet
import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet
import com.eatmoreduck.ruleengine.admin.auth.SysRolesTable
import com.eatmoreduck.ruleengine.admin.auth.SysUserRolesTable
import com.eatmoreduck.ruleengine.admin.auth.SysUsersTable
import com.eatmoreduck.ruleengine.dsl.DslJson
import com.eatmoreduck.ruleengine.storage.log.ExecutionLogsTable
import org.hamcrest.Matchers
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * admin-api 查询面（监控指标 + 执行日志 + 效果分析 + 冲突检测 + 功能开关 + 系统管理 + 404 兜底）契约测试。
 *
 * 与 AdminApiContractTest 同款设施：Testcontainers PG16（真实 PostgreSQL + Flyway V1__init
 * 迁移 + 权限种子）+ 完整 Spring 上下文（Sa-Token 拦截链）+ MockMvc；断言口径为前端消费方
 * （frontend/src/api 与 frontend/src/types）的字段名与解析逻辑；无 Docker 的环境自动跳过。
 *
 * 数据面说明：旧实现的监控指标来自进程内 Micrometer（重启清零），新架构由
 * execution_logs 持久化聚合，测试直接向 execution_logs 播种日志行驱动全部断言。
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@SpringBootTest
@DisplayName("admin-api 契约：监控指标 + 执行日志 + 效果分析 + 冲突检测 + 功能开关 + 系统管理")
class AdminApiInsightContractTest {
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
            registry.add("ruleengine.storage.url") {
                val url = postgres.jdbcUrl
                if (url.contains("?")) "$url&stringtype=unspecified" else "$url?stringtype=unspecified"
            }
            registry.add("ruleengine.storage.username", postgres::getUsername)
            registry.add("ruleengine.storage.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var applicationContext: WebApplicationContext

    /** MockMvc 懒初始化（Sa-Token 上下文过滤器显式挂载，与 AdminApiContractTest 一致） */
    private val mockMvc: MockMvc by lazy {
        val builder = MockMvcBuilders.webAppContextSetup(applicationContext)
        builder.addFilters<DefaultMockMvcBuilder>(
            SaFirewallCheckFilterForJakartaServlet(),
            SaTokenContextFilterForJakartaServlet(),
        )
        builder.build()
    }

    private val token: String by lazy { login() }

    /** 业务种子数据（规则经 API 创建 + execution_logs 直插；全部测试共享，只执行一次） */
    private val seeded: Boolean by lazy { seedData() }

    private val highRule = "insight_high_amount"
    private val lowRule = "insight_low_amount"

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
    ) = mockMvc.perform(MockMvcRequestBuilders.get(path).apply { token?.let { header("Authorization", it) } })

    private fun post(
        path: String,
        token: String? = null,
        body: String? = null,
    ) = mockMvc
        .perform(
            MockMvcRequestBuilders.post(path).apply {
                token?.let { header("Authorization", it) }
                body?.let {
                    contentType("application/json")
                    content(it)
                }
            },
        )

    private fun json(raw: String): String = DslJson.mapper.writeValueAsString(raw)

    /** 读取 GET 的 JSON 数组响应体（filter 型 JSONPath 对空值/嵌套结构不可靠处改用树断言） */
    private fun arrayBody(path: String): List<JsonNode> = readArray(get(path, token))

    /** 断言请求成功并读取 JSON 数组响应体（冲突检测族为 POST） */
    private fun readArray(request: org.springframework.test.web.servlet.ResultActions): List<JsonNode> {
        val body =
            request
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
                .response
                .contentAsString
        return DslJson.mapper.readTree(body).toList()
    }

    /** 播种一个 VIEWER 用户（无系统管理权限码） */
    private fun seedViewer() {
        transaction {
            val roleId =
                SysRolesTable
                    .select(SysRolesTable.id)
                    .where { SysRolesTable.roleCode eq "VIEWER" }
                    .single()[SysRolesTable.id]
            val userId =
                SysUsersTable.insert { statement ->
                    statement[SysUsersTable.username] = "insight_viewer"
                    statement[SysUsersTable.password] = BCryptPasswordEncoder().encode("viewer-pass")!!
                    statement[SysUsersTable.nickname] = "只读用户"
                    statement[SysUsersTable.status] = "ACTIVE"
                    statement[SysUsersTable.createdAt] = Instant.now()
                } get SysUsersTable.id
            SysUserRolesTable.insert { statement ->
                statement[SysUserRolesTable.userId] = userId
                statement[SysUserRolesTable.roleId] = roleId
            }
        }
    }

    // ---------- 种子数据 ----------

    /**
     * 播种数据布局（execution_logs，时区取 JVM 默认）：
     * - insight_high_amount：昨天 1 条 SUCCESS/PASS/10ms + 今天 3 条（SUCCESS/PASS/20ms、ERROR/REJECT/5ms、TIMEOUT/REJECT/200ms）
     * - insight_low_amount：今天 1 条 SUCCESS/REJECT/8ms
     * 另创建两条无特征引用的常量规则（only REJECT / only PASS）驱动决策冲突检测。
     */
    private fun seedData(): Boolean {
        // PER_METHOD 生命周期下每个测试方法一个新实例，lazy 不跨方法共享：
        // 以"高额规则已有日志"作为播种完成标记，保证幂等（部分失败也只重试缺失部分）
        if (logsSeeded()) return true
        createRuleIfAbsent(highRule, "高额订单规则", "def amt = features.order_amount\nreturn amt > 1000 ? 'REJECT' : 'PASS'")
        createRuleIfAbsent(lowRule, "小额订单规则", "def amt = features.order_amount\nreturn amt < 500 ? 'REJECT' : 'PASS'")
        createRuleIfAbsent("insight_always_reject", "常量拒绝规则", "return 'REJECT'")
        createRuleIfAbsent("insight_always_pass", "常量放行规则", "return 'PASS'")

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        transaction {
            fun insertLog(
                ruleKey: String,
                decision: String,
                status: String,
                timeMs: Int,
                at: Instant,
                errorMessage: String? = null,
            ) {
                ExecutionLogsTable.insert { statement ->
                    statement[ExecutionLogsTable.ruleKey] = ruleKey
                    statement[ruleVersion] = 1
                    statement[inputFeatures] = """{"order_amount":1500}"""
                    statement[outputDecision] = decision
                    statement[outputReason] = "契约测试"
                    statement[executionTimeMs] = timeMs
                    statement[ExecutionLogsTable.status] = status
                    statement[ExecutionLogsTable.errorMessage] = errorMessage
                    statement[createdAt] = at
                }
            }
            insertLog(highRule, "PASS", "SUCCESS", 10, yesterday.atTime(10, 0).atZone(zone).toInstant())
            insertLog(highRule, "PASS", "SUCCESS", 20, today.atTime(9, 0).atZone(zone).toInstant())
            insertLog(highRule, "REJECT", "ERROR", 5, today.atTime(10, 0).atZone(zone).toInstant(), errorMessage = "boom")
            insertLog(highRule, "REJECT", "TIMEOUT", 200, today.atTime(11, 0).atZone(zone).toInstant())
            insertLog(lowRule, "REJECT", "SUCCESS", 8, today.atTime(9, 30).atZone(zone).toInstant())
        }
        return true
    }

    /** 播种完成标记：高额规则已出现执行日志（规则创建成功且日志批次已落库） */
    private fun logsSeeded(): Boolean =
        transaction {
            ExecutionLogsTable
                .selectAll()
                .where { ExecutionLogsTable.ruleKey eq highRule }
                .any()
        }

    private fun createRuleIfAbsent(
        ruleKey: String,
        ruleName: String,
        script: String,
    ) {
        val existing =
            get("/api/v1/rules/$ruleKey", token)
                .andReturn()
                .response
                .status
        if (existing == 200) return
        post(
            "/api/v1/rules",
            token,
            """{"ruleKey":"$ruleKey","ruleName":"$ruleName","groovyScript":${json(script)}}""",
        ).andExpect(MockMvcResultMatchers.status().isOk)
    }

    private fun LocalDate.atTime(
        hour: Int,
        minute: Int,
    ): java.time.LocalDateTime = atTime(LocalTime.of(hour, minute))

    // ---------- 鉴权与 404 兜底 ----------

    @Test
    @Order(10)
    @DisplayName("未登录访问查询面接口返回 401 旧契约结构")
    fun unauthorizedHasLegacyErrorShape() {
        get("/api/v1/metrics/overview")
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(401))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("未登录或登录已过期"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.error").value("Unauthorized"))
    }

    @Test
    @Order(11)
    @DisplayName("未知路径返回 404 旧契约结构（不再兜底 500）")
    fun unknownPathReturnsNotFoundShape() {
        get("/api/v1/nonexistent/path", token)
            .andExpect(MockMvcResultMatchers.status().isNotFound)
            .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(404))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("资源不存在: api/v1/nonexistent/path"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.error").value("Not Found"))
    }

    // ---------- 监控指标 ----------

    @Test
    @Order(20)
    @DisplayName("监控总览按 execution_logs 聚合六个字段")
    fun metricsOverviewAggregatesExecutionLogs() {
        seeded
        // 5 条日志：2 命中（PASS）、1 错误（ERROR）、总耗时 10+20+5+200+8=243ms
        get("/api/v1/metrics/overview", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalExecutions").value(5))
            .andExpect(MockMvcResultMatchers.jsonPath("$.hitCount").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.hitRate").value(40.0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.avgExecutionTime").value(48.6))
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCount").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorRate").value(20.0))
    }

    @Test
    @Order(21)
    @DisplayName("规则执行排行按执行次数降序并支持 limit 截断")
    fun metricsRankingSortsAndLimits() {
        seeded
        get("/api/v1/metrics/rules?sortBy=executionCount&sortOrder=desc&limit=10", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].ruleKey").value(highRule))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].ruleName").value("高额订单规则"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].executionCount").value(4))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].hitCount").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].hitRate").value(50.0))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].avgExecutionTime").value(58.75))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].errorCount").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].enabled").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$[1].ruleKey").value(lowRule))
            .andExpect(MockMvcResultMatchers.jsonPath("$[1].executionCount").value(1))
        get("/api/v1/metrics/rules?limit=1", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
    }

    @Test
    @Order(22)
    @DisplayName("单规则执行统计返回含 P95 分位与最后执行时间")
    fun metricsRuleStatsReturnsPercentiles() {
        seeded
        get("/api/v1/metrics/rules/$highRule", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalExecutions").value(4))
            .andExpect(MockMvcResultMatchers.jsonPath("$.hitCount").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCount").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.avgExecutionTimeMs").value(58.75))
            .andExpect(MockMvcResultMatchers.jsonPath("$.p95ExecutionTimeMs").value(200.0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.lastExecutedAt").isNotEmpty)
    }

    // ---------- 执行日志 ----------

    @Test
    @Order(30)
    @DisplayName("最近日志映射旧字段（result/level/ruleName）并支持 limit 与级别过滤")
    fun recentLogsMapLegacyFields() {
        seeded
        // 全量 5 条（窗口 100），created_at 降序：TIMEOUT 行最前
        get("/api/v1/logs/recent?limit=2", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").isNumber)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].ruleKey").value(highRule))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].ruleName").value(highRule))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].result").value("MISS"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].executionTime").value(200))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].level").value("WARN"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].executedAt").isNotEmpty)
            .andExpect(MockMvcResultMatchers.jsonPath("$[1].level").value("ERROR"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[1].result").value("ERROR"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[1].errorMessage").value("boom"))
        // 级别过滤：ERROR 级只有 1 条
        get("/api/v1/logs/recent?level=ERROR", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].level").value("ERROR"))
    }

    @Test
    @Order(31)
    @DisplayName("按规则/状态/时间窗查询执行日志")
    fun logsByRuleKeyStatusAndTimeRange() {
        seeded
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        val start = today.atStartOfDay().format(formatter)
        val end = today.plusDays(1).atStartOfDay().format(formatter)
        get("/api/v1/logs/rules/$highRule", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(4))
        // 时间窗（start/end 同时提供才生效）：今天的 3 条
        get("/api/v1/logs/rules/$highRule?start=$start&end=$end", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(3))
        get("/api/v1/logs/status/ERROR", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].ruleKey").value(highRule))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].level").value("ERROR"))
        get("/api/v1/logs/status/TIMEOUT", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].level").value("WARN"))
    }

    // ---------- 效果分析 ----------

    @Test
    @Order(40)
    @DisplayName("分析概览按规则聚合并输出逐日趋势（口径：hit=status SUCCESS）")
    fun analyticsOverviewBuildsTrendData() {
        seeded
        val yesterday = LocalDate.now().minusDays(1).toString()
        val today = LocalDate.now().toString()
        val high =
            arrayBody("/api/v1/analytics/overview?startDate=$yesterday&endDate=$today")
                .single { it.get("ruleKey").asText() == highRule }
        assertEquals("高额订单规则", high.get("ruleName").asText())
        assertEquals(4, high.get("totalExecutions").asLong())
        assertEquals(2, high.get("hitCount").asLong())
        assertEquals(50.0, high.get("hitRate").asDouble(), 0.0001)
        assertEquals(2, high.get("rejectCount").asLong())
        assertEquals(2, high.get("passCount").asLong())
        assertEquals(2, high.get("errorCount").asLong())
        assertEquals(58.75, high.get("avgExecutionTimeMs").asDouble(), 0.0001)
        assertEquals(200.0, high.get("maxExecutionTimeMs").asDouble(), 0.0001)
        assertEquals(200.0, high.get("p99ExecutionTimeMs").asDouble(), 0.0001)
        val trend = high.get("trendData").toList()
        assertEquals(2, trend.size)
        assertEquals(yesterday, trend[0].get("date").asText())
        assertEquals(today, trend[1].get("date").asText())
        assertEquals(1, trend[0].get("executions").asLong())
        assertEquals(1, trend[0].get("hits").asLong())
        assertEquals(3, trend[1].get("executions").asLong())
        assertEquals(1, trend[1].get("hits").asLong())

        val low =
            arrayBody("/api/v1/analytics/overview?startDate=$yesterday&endDate=$today")
                .single { it.get("ruleKey").asText() == lowRule }
        assertEquals(1, low.get("totalExecutions").asLong())
    }

    @Test
    @Order(41)
    @DisplayName("单规则效果分析返回同口径数据")
    fun analyticsForSingleRule() {
        seeded
        get("/api/v1/analytics/rules/$lowRule", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.ruleKey").value(lowRule))
            .andExpect(MockMvcResultMatchers.jsonPath("$.ruleName").value("小额订单规则"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalExecutions").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.rejectCount").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.trendData").isArray)
    }

    @Test
    @Order(42)
    @DisplayName("依赖图识别共享特征的规则连接")
    fun dependencyGraphSharesFeatures() {
        seeded
        get("/api/v1/analytics/dependencies", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.nodes").isArray)
            .andExpect(MockMvcResultMatchers.jsonPath("$.nodes.length()").value(4))
            .andExpect(MockMvcResultMatchers.jsonPath("$.nodes[0].ruleKey").value(highRule))
            .andExpect(MockMvcResultMatchers.jsonPath("$.nodes[0].features[0]").value("order_amount"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.edges.length()").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.edges[0].source").value(highRule))
            .andExpect(MockMvcResultMatchers.jsonPath("$.edges[0].target").value(lowRule))
            .andExpect(MockMvcResultMatchers.jsonPath("$.edges[0].dependencyType").value("FEATURE_DEPENDENCY"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.edges[0].sharedFeatureList[0]").value("order_amount"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.sharedFeatures[0]").value("order_amount"))
        get("/api/v1/analytics/dependencies/$highRule", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.nodes.length()").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.edges.length()").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.edges[0].source").value(highRule))
    }

    // ---------- 冲突检测 ----------

    @Test
    @Order(50)
    @DisplayName("全量冲突检测识别条件互斥（HIGH）与决策矛盾（MEDIUM）")
    fun conflictDetectionFindsConditionAndDecisionConflicts() {
        seeded
        val conflicts = readArray(post("/api/v1/conflicts/detect", token))

        // 高额(amt > 1000) vs 小额(amt < 500)：互斥范围 → CONDITION_CONFLICT / HIGH
        val conditionConflicts = conflicts.filter { it.get("conflictType").asText() == "CONDITION_CONFLICT" }
        assertEquals(1, conditionConflicts.size)
        val condition = conditionConflicts.single()
        assertEquals(highRule, condition.get("ruleKey1").asText())
        assertEquals("高额订单规则", condition.get("ruleName1").asText())
        assertEquals(lowRule, condition.get("ruleKey2").asText())
        assertEquals("小额订单规则", condition.get("ruleName2").asText())
        assertEquals("HIGH", condition.get("severity").asText())
        assertTrue(condition.get("description").asText().contains("变量 'amt' 存在冲突条件"))
        assertTrue(condition.get("description").asText().contains("amt > 1000"))
        assertTrue(condition.get("description").asText().contains("amt < 500"))

        // 常量拒绝 vs 常量放行：决策矛盾 → DECISION_CONFLICT / MEDIUM
        val decisionConflicts = conflicts.filter { it.get("conflictType").asText() == "DECISION_CONFLICT" }
        assertTrue(decisionConflicts.isNotEmpty())
        val alwaysPair =
            decisionConflicts.single {
                setOf(it.get("ruleKey1").asText(), it.get("ruleKey2").asText()) ==
                    setOf("insight_always_reject", "insight_always_pass")
            }
        assertEquals("MEDIUM", alwaysPair.get("severity").asText())
        assertTrue(alwaysPair.get("description").asText().contains("可能对相同输入产生不同决策结果"))
    }

    @Test
    @Order(51)
    @DisplayName("单规则冲突检测限定目标规则的冲突对")
    fun conflictDetectionForSingleRule() {
        seeded
        val conflicts = arrayBody("/api/v1/conflicts/rule/$highRule")
        val conditionConflicts = conflicts.filter { it.get("conflictType").asText() == "CONDITION_CONFLICT" }
        assertEquals(1, conditionConflicts.size)
        assertEquals(lowRule, conditionConflicts.single().get("ruleKey2").asText())
        // 冲突对无序（目标规则可能在 ruleKey1 或 ruleKey2 侧）
        val others = conflicts.map { setOf(it.get("ruleKey1").asText(), it.get("ruleKey2").asText()) }
        assertTrue(others.all { highRule in it })
        assertEquals(emptyList<String>(), arrayBody("/api/v1/conflicts/rule/insight_missing_rule"))
    }

    // ---------- 功能开关 ----------

    @Test
    @Order(60)
    @DisplayName("功能开关返回固定关闭的两个键")
    fun featureFlagsAreAllDisabled() {
        seeded
        get("/api/v1/features", token)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.multiEnvironment").value(false))
            .andExpect(MockMvcResultMatchers.jsonPath("$.importExport").value(false))
    }

    // ---------- 系统管理 ----------

    @Test
    @Order(70)
    @DisplayName("系统用户列表含角色关联与旧字段集")
    fun systemUsersIncludeRoles() {
        seeded
        val admin = arrayBody("/api/v1/system/users").single { it.get("username").asText() == "admin" }
        assertTrue(admin.get("id").asLong() > 0)
        assertEquals("系统管理员", admin.get("nickname").asText())
        assertEquals("ACTIVE", admin.get("status").asText())
        assertTrue(admin.has("email"))
        assertTrue(admin.has("phone"))
        assertTrue(admin.has("createdAt"))
        val roles = admin.get("roles").toList()
        assertEquals(1, roles.size)
        assertEquals("SUPER_ADMIN", roles[0].get("roleCode").asText())
        assertEquals("超级管理员", roles[0].get("roleName").asText())
        assertTrue(roles[0].has("description"))
        assertTrue(roles[0].has("status"))
    }

    @Test
    @Order(71)
    @DisplayName("系统角色列表含权限码与描述")
    fun systemRolesIncludePermissionCodes() {
        seeded
        val superAdmin = arrayBody("/api/v1/system/roles").single { it.get("roleCode").asText() == "SUPER_ADMIN" }
        assertEquals("超级管理员", superAdmin.get("roleName").asText())
        assertEquals("拥有系统全部权限", superAdmin.get("description").asText())
        assertEquals("ACTIVE", superAdmin.get("status").asText())
        val codes = superAdmin.get("permissionCodes").toList().map { it.asText() }
        assertTrue(codes.contains("api:rules:create"))
        assertTrue(codes.contains("api:system:user:view"))
        val viewer = arrayBody("/api/v1/system/roles").single { it.get("roleCode").asText() == "VIEWER" }
        assertTrue(
            viewer
                .get("permissionCodes")
                .toList()
                .map { it.asText() }
                .contains("api:rules:view"),
        )
    }

    @Test
    @Order(72)
    @DisplayName("系统权限清单含权限树元数据字段")
    fun systemPermissionsIncludeTreeMetadata() {
        seeded
        val permissions = arrayBody("/api/v1/system/permissions")
        val userView = permissions.single { it.get("permissionCode").asText() == "api:system:user:view" }
        assertEquals("查看用户", userView.get("permissionName").asText())
        assertEquals("API", userView.get("resourceType").asText())
        assertEquals("/api/v1/system/users", userView.get("resourcePath").asText())
        assertEquals("GET", userView.get("method").asText())
        assertEquals(10L, userView.get("parentId").asLong())
        assertEquals(1, userView.get("sortOrder").asInt())
        val menu = permissions.single { it.get("permissionCode").asText() == "menu:rules" }
        assertEquals("MENU", menu.get("resourceType").asText())
        assertTrue(menu.get("parentId").isNull)
        assertEquals(1, menu.get("sortOrder").asInt())
    }

    @Test
    @Order(73)
    @DisplayName("无权限用户访问系统管理接口返回 403 旧契约结构")
    fun forbiddenForViewerOnSystemEndpoints() {
        seeded
        seedViewer()
        val viewer = login("insight_viewer", "viewer-pass")
        // VIEWER 无 api:system:user:view / api:system:role:view
        get("/api/v1/system/users", viewer)
            .andExpect(MockMvcResultMatchers.status().isForbidden)
            .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(403))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("无操作权限: api:system:user:view"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.error").value("Forbidden"))
        get("/api/v1/system/permissions", viewer)
            .andExpect(MockMvcResultMatchers.status().isForbidden)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("无操作权限: api:system:role:view"))
        // 监控/日志/分析/冲突检测族仅要求登录（旧契约无方法级权限码），VIEWER 可读
        get("/api/v1/metrics/overview", viewer).andExpect(MockMvcResultMatchers.status().isOk)
        get("/api/v1/logs/recent", viewer).andExpect(MockMvcResultMatchers.status().isOk)
        get("/api/v1/analytics/dependencies", viewer).andExpect(MockMvcResultMatchers.status().isOk)
        post("/api/v1/conflicts/detect", viewer).andExpect(MockMvcResultMatchers.status().isOk)
        get("/api/v1/features", viewer).andExpect(MockMvcResultMatchers.status().isOk)
    }
}
