package com.eatmoreduck.ruleengine.decision

import com.eatmoreduck.ruleengine.storage.table.RuleVersionsTable
import com.eatmoreduck.ruleengine.storage.table.RulesTable
import org.jetbrains.exposed.v1.jdbc.insert
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
 * 决策性能冒烟（阶段 6 压测前的本地基线）：预热后循环 1000 次 cache-aware 决策
 * （快照缓存 + 编译缓存全命中路径，含真实 PG 仓储冷启动），输出 p50/p95/p99/max 与超 SLA 占比。
 *
 * 不设硬门槛（按任务约定如实报告）——MockMvc 进程内调用不含网络栈开销，
 * 真实 HTTP 链路数字以阶段 6 的 K6 压测为准。
 * 无 Docker 环境自动跳过。
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@SpringBootTest(
    classes = [com.eatmoreduck.ruleengine.admin.AdminApiApplication::class],
    properties = ["sa-token.auth-enabled=false"],
)
@DisplayName("decision-api 性能冒烟：1000 次决策的 p50/p95 分位")
class DecisionPerformanceSmokeTest {
    companion object {
        private const val RULE_KEY = "perf_rule"
        private const val ITERATIONS = 1000
        private const val WARMUP = 200

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
            val url = postgres.jdbcUrl
            registry.add(
                "ruleengine.storage.url",
            ) { if (url.contains("?")) "$url&stringtype=unspecified" else "$url?stringtype=unspecified" }
            registry.add("ruleengine.storage.username", postgres::getUsername)
            registry.add("ruleengine.storage.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var applicationContext: WebApplicationContext

    private val mockMvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(applicationContext).build() }

    private fun decideOnce(): Long {
        val start = System.nanoTime()
        val request =
            MockMvcRequestBuilders
                .post("/api/v1/decide/$RULE_KEY")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"order_amount":100,"userId":"perf-user"}""")
        val initial = mockMvc.perform(request)
        // suspend controller 启动异步请求，asyncDispatch 取最终响应
        val result =
            if (initial.andReturn().request.isAsyncStarted) {
                mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(initial.andReturn()))
            } else {
                initial
            }
        result
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.decision").value("PASS"))
        return TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start)
    }

    @Test
    @Order(1)
    @DisplayName("预热 + 1000 次决策，输出 p50/p95/p99/max（微秒）")
    fun measureLatency() {
        // 种子：单规则 ACTIVE v1（Groovy 脚本）
        transaction {
            val ruleId =
                RulesTable.insert { statement ->
                    statement[RulesTable.ruleKey] = RULE_KEY
                    statement[ruleName] = "性能冒烟规则"
                    statement[groovyScript] = ""
                    statement[version] = 1
                    statement[activeVersion] = 1
                    statement[enabled] = true
                    statement[deleted] = false
                    statement[createdBy] = "perf-test"
                    statement[createdAt] = Instant.now()
                } get RulesTable.id
            RuleVersionsTable.insert { statement ->
                statement[RuleVersionsTable.ruleId] = ruleId
                statement[RuleVersionsTable.ruleKey] = RULE_KEY
                statement[RuleVersionsTable.version] = 1
                statement[RuleVersionsTable.groovyScript] = "return features.order_amount > 10000 ? 'REJECT' : 'PASS'"
                statement[RuleVersionsTable.changedBy] = "perf-test"
                statement[RuleVersionsTable.changedAt] = Instant.now()
                statement[RuleVersionsTable.status] = "ACTIVE"
            }
        }

        // 预热：首查走 DB + 编译，后续命中两层缓存
        repeat(WARMUP) { decideOnce() }

        val samples = LongArray(ITERATIONS)
        repeat(ITERATIONS) { i -> samples[i] = decideOnce() }

        val sorted = samples.sorted()

        fun percentile(p: Double): Long = sorted[(p * (sorted.size - 1)).toInt()]

        val p50 = percentile(0.50)
        val p95 = percentile(0.95)
        val p99 = percentile(0.99)
        val max = sorted.last()
        val mean = samples.average().toLong()
        val overSla = sorted.count { it > 50_000 } // 50ms SLA

        println(
            """
            |
            |===== 决策性能冒烟（进程内 MockMvc，$ITERATIONS 次缓存命中路径） =====
            |  p50 = $p50 us
            |  p95 = $p95 us
            |  p99 = $p99 us
            |  max = $max us
            |  avg = $mean us
            |  超 50ms 样本 = $overSla / $ITERATIONS
            |===========================================================
            """.trimMargin(),
        )
    }
}
