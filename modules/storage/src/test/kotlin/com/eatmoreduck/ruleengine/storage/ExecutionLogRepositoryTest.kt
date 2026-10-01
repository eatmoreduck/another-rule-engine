package com.eatmoreduck.ruleengine.storage

import com.eatmoreduck.ruleengine.storage.log.CanaryExecutionLogRow
import com.eatmoreduck.ruleengine.storage.log.CanaryExecutionLogTable
import com.eatmoreduck.ruleengine.storage.log.ExecutionLogRow
import com.eatmoreduck.ruleengine.storage.log.ExecutionLogsTable
import com.eatmoreduck.ruleengine.storage.log.ExposedCanaryExecutionLogRepository
import com.eatmoreduck.ruleengine.storage.log.ExposedExecutionLogRepository
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 执行日志仓储映射测试（H2 内存库，SchemaUtils 生成 DDL）。
 *
 * 真实 PostgreSQL 下 `input_features` / `request_features` 为 JSONB 列（文本写入经
 * `stringtype=unspecified` 隐式转换），H2 侧退化为 TEXT 列验证列绑定与往返逻辑；
 * 真实库端到端核对由 decision-api 的契约测试承担。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExecutionLogRepositoryTest {
    private val executionLogRepository = ExposedExecutionLogRepository()
    private val canaryLogRepository = ExposedCanaryExecutionLogRepository()

    @BeforeAll
    fun connectAndCreateSchema() {
        val dataSource =
            JdbcDataSource().apply {
                setURL("jdbc:h2:mem:execution-log-repo;DB_CLOSE_DELAY=-1")
            }
        Database.connect(dataSource)
        transaction {
            SchemaUtils.create(ExecutionLogsTable, CanaryExecutionLogTable)
        }
    }

    @Test
    fun `insertBatch persists execution log rows and reads them back`() {
        val createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val rows =
            listOf(
                ExecutionLogRow(
                    ruleKey = "high_amount_rule",
                    ruleVersion = 3,
                    inputFeatures = """{"order_amount":1500.0,"user_level":"VIP"}""",
                    outputDecision = "REJECT",
                    outputReason = "金额超限",
                    executionTimeMs = 12,
                    status = "SUCCESS",
                    errorMessage = null,
                    createdAt = createdAt,
                ),
                ExecutionLogRow(
                    ruleKey = "slow_rule",
                    ruleVersion = null,
                    inputFeatures = null,
                    outputDecision = "REJECT",
                    outputReason = "规则执行超时",
                    executionTimeMs = 200,
                    status = "TIMEOUT",
                    errorMessage = null,
                    createdAt = createdAt,
                ),
                ExecutionLogRow(
                    ruleKey = "broken_rule",
                    ruleVersion = 1,
                    inputFeatures = """{"a":1}""",
                    outputDecision = "REJECT",
                    outputReason = "规则执行失败: boom",
                    executionTimeMs = 3,
                    status = "ERROR",
                    errorMessage = "boom",
                    createdAt = createdAt,
                ),
            )

        val inserted =
            transaction {
                executionLogRepository.insertBatch(rows)
            }

        assertEquals(3, inserted)
        transaction {
            val all =
                ExecutionLogsTable
                    .selectAll()
                    .map { it }
            assertEquals(3, all.size)
            val successRow = all.single { it[ExecutionLogsTable.status] == "SUCCESS" }
            assertEquals("high_amount_rule", successRow[ExecutionLogsTable.ruleKey])
            assertEquals(3, successRow[ExecutionLogsTable.ruleVersion])
            assertEquals("REJECT", successRow[ExecutionLogsTable.outputDecision])
            assertEquals("金额超限", successRow[ExecutionLogsTable.outputReason])
            assertEquals(12, successRow[ExecutionLogsTable.executionTimeMs])
            assertEquals(
                createdAt.truncatedTo(ChronoUnit.SECONDS),
                successRow[ExecutionLogsTable.createdAt].truncatedTo(ChronoUnit.SECONDS),
            )
            val errorRow = all.single { it[ExecutionLogsTable.status] == "ERROR" }
            assertEquals("boom", errorRow[ExecutionLogsTable.errorMessage])
        }
    }

    @Test
    fun `query methods filter sort and aggregate execution logs`() {
        // 时间基准取未来，保证与首个用例插入的历史行在时间窗断言上互不干扰
        val base = Instant.now().plusSeconds(100_000).truncatedTo(ChronoUnit.MINUTES)
        val before = transaction { executionLogRepository.aggregateTotal() }
        transaction {
            executionLogRepository.insertBatch(
                listOf(
                    ExecutionLogRow(
                        ruleKey = "rule_a",
                        ruleVersion = 1,
                        inputFeatures = null,
                        outputDecision = "PASS",
                        outputReason = null,
                        executionTimeMs = 10,
                        status = "SUCCESS",
                        errorMessage = null,
                        createdAt = base,
                    ),
                    ExecutionLogRow(
                        ruleKey = "rule_a",
                        ruleVersion = 1,
                        inputFeatures = null,
                        outputDecision = "REJECT",
                        outputReason = null,
                        executionTimeMs = 30,
                        status = "ERROR",
                        errorMessage = "boom",
                        createdAt = base.plusSeconds(60),
                    ),
                    ExecutionLogRow(
                        ruleKey = "rule_b",
                        ruleVersion = null,
                        inputFeatures = null,
                        outputDecision = "PASS",
                        outputReason = null,
                        executionTimeMs = 20,
                        status = "TIMEOUT",
                        errorMessage = null,
                        createdAt = base.plusSeconds(120),
                    ),
                ),
            )
        }

        transaction {
            // 按规则查询：降序 + 读模型字段完整
            val byRule = executionLogRepository.findLogsByRuleKey("rule_a")
            assertEquals(2, byRule.size)
            assertTrue(byRule[0].createdAt >= byRule[1].createdAt)
            assertEquals("ERROR", byRule[0].status)
            assertEquals("boom", byRule[0].errorMessage)
            assertTrue(byRule[0].id > 0)

            // 时间窗（闭区间）裁剪
            assertEquals(1, executionLogRepository.findLogsByRuleKeyAndTimeRange("rule_a", base.plusSeconds(60), base.plusSeconds(60)).size)
            assertEquals(0, executionLogRepository.findLogsByTimeRange(base.plusSeconds(200), base.plusSeconds(300)).size)
            assertEquals(3, executionLogRepository.findLogsByTimeRange(base, base.plusSeconds(120)).size)

            // 状态过滤（历史用例也有 TIMEOUT 行，按规则定位）与最近日志
            assertEquals(1, executionLogRepository.findLogsByStatus("TIMEOUT").count { it.ruleKey == "rule_b" })
            assertEquals(2, executionLogRepository.findRecentLogs(2).size)

            // 全表聚合（相对增量断言，不受历史用例行影响）：命中 = PASS 决策，错误 = ERROR 状态
            val summary = executionLogRepository.aggregateTotal()
            assertEquals(before.totalExecutions + 3, summary.totalExecutions)
            assertEquals(before.hitCount + 2, summary.hitCount)
            assertEquals(before.errorCount + 1, summary.errorCount)

            // 分组聚合：按 ruleKey 定位本用例行
            val perRule = executionLogRepository.aggregatePerRule()
            val ruleA = perRule.first { it.ruleKey == "rule_a" }
            assertEquals(2L, ruleA.executionCount)
            assertEquals(1L, ruleA.hitCount)
            assertEquals(1L, ruleA.errorCount)
            assertEquals(20.0, ruleA.avgExecutionTimeMs, 0.0001)
            // rule_b 单条 PASS 决策：命中 1、错误 0
            assertEquals(1L, perRule.first { it.ruleKey == "rule_b" }.hitCount)
            assertEquals(0L, perRule.first { it.ruleKey == "rule_b" }.errorCount)
        }
    }

    @Test
    fun `insertBatch persists canary execution log rows`() {
        val rows =
            listOf(
                CanaryExecutionLogRow(
                    traceId = "trace-1",
                    targetType = "RULE",
                    targetKey = "high_amount_rule",
                    versionUsed = 2,
                    isCanary = true,
                    requestFeatures = """{"userId":"u-100"}""",
                    decisionResult = "PASS",
                    executionTimeMs = 9L,
                    errorMessage = null,
                    createdAt = Instant.now(),
                ),
                CanaryExecutionLogRow(
                    traceId = "trace-2",
                    targetType = "DECISION_FLOW",
                    targetKey = "fraud_flow",
                    versionUsed = 0,
                    isCanary = false,
                    requestFeatures = null,
                    decisionResult = null,
                    executionTimeMs = 40L,
                    errorMessage = "决策流执行失败: 没有开始节点",
                    createdAt = Instant.now(),
                ),
            )

        val inserted =
            transaction {
                canaryLogRepository.insertBatch(rows)
            }

        assertEquals(2, inserted)
        transaction {
            val all = CanaryExecutionLogTable.selectAll().map { it }
            assertEquals(2, all.size)
            val canary = all.single { it[CanaryExecutionLogTable.isCanary] }
            assertEquals("RULE", canary[CanaryExecutionLogTable.targetType])
            assertEquals("trace-1", canary[CanaryExecutionLogTable.traceId])
            assertEquals(2, canary[CanaryExecutionLogTable.versionUsed])
            assertEquals("PASS", canary[CanaryExecutionLogTable.decisionResult])
            val errorRow = all.single { !it[CanaryExecutionLogTable.isCanary] }
            assertTrue(errorRow[CanaryExecutionLogTable.errorMessage]!!.contains("没有开始节点"))
            assertEquals("DECISION_FLOW", errorRow[CanaryExecutionLogTable.targetType])
        }
    }
}
