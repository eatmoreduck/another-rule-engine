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
