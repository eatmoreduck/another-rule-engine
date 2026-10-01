package com.eatmoreduck.ruleengine.storage.log

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.avg
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.math.BigDecimal
import java.time.Instant

/** 聚合口径常量：命中判定（与旧 RuleExecutionMetrics.recordExecution 一致，大小写不敏感的判定在 SQL 侧收窄为精确匹配） */
private const val DECISION_PASS = "PASS"

/** 聚合口径常量：错误状态（与旧 RuleExecutionMetrics.recordError 一致） */
private const val STATUS_ERROR = "ERROR"

/**
 * [ExecutionLogRepository] 的 Exposed 实现：单事务逐行 insert（PG 端经
 * JDBC 批量优化后仍是同事务原子提交，量级为每次刷盘数十~数百行，足够），
 * 以及 admin-api 查询面的只读 SELECT。
 *
 * 不标注 @Repository：final class 无法被注解驱动的 CGLIB 代理，
 * 经 [LogStorageConfiguration] 显式 @Bean 注册（与 StorageConfiguration 的显式装配风格一致）。
 */
class ExposedExecutionLogRepository : ExecutionLogRepository {
    override fun insertBatch(rows: List<ExecutionLogRow>): Int {
        rows.forEach { row ->
            ExecutionLogsTable.insert { statement ->
                statement[ruleKey] = row.ruleKey
                statement[ruleVersion] = row.ruleVersion
                statement[inputFeatures] = row.inputFeatures
                statement[outputDecision] = row.outputDecision
                statement[outputReason] = row.outputReason
                statement[executionTimeMs] = row.executionTimeMs
                statement[status] = row.status
                statement[errorMessage] = row.errorMessage
                statement[createdAt] = row.createdAt
            }
        }
        return rows.size
    }

    override fun findLogsByRuleKey(ruleKey: String): List<ExecutionLogEntry> =
        ExecutionLogsTable
            .selectAll()
            .where { ExecutionLogsTable.ruleKey eq ruleKey }
            .orderByCreatedAtDesc()
            .map(::toEntry)

    override fun findLogsByRuleKeyAndTimeRange(
        ruleKey: String,
        start: Instant,
        end: Instant,
    ): List<ExecutionLogEntry> =
        ExecutionLogsTable
            .selectAll()
            .where {
                (ExecutionLogsTable.ruleKey eq ruleKey) and
                    (ExecutionLogsTable.createdAt greaterEq start) and
                    (ExecutionLogsTable.createdAt lessEq end)
            }.orderByCreatedAtDesc()
            .map(::toEntry)

    override fun findLogsByStatus(status: String): List<ExecutionLogEntry> =
        ExecutionLogsTable
            .selectAll()
            .where { ExecutionLogsTable.status eq status }
            .orderByCreatedAtDesc()
            .map(::toEntry)

    override fun findRecentLogs(limit: Int): List<ExecutionLogEntry> =
        ExecutionLogsTable
            .selectAll()
            .orderByCreatedAtDesc()
            .limit(limit)
            .map(::toEntry)

    override fun findLogsByTimeRange(
        start: Instant,
        end: Instant,
    ): List<ExecutionLogEntry> =
        ExecutionLogsTable
            .selectAll()
            .where {
                (ExecutionLogsTable.createdAt greaterEq start) and
                    (ExecutionLogsTable.createdAt lessEq end)
            }.orderByCreatedAtDesc()
            .map(::toEntry)

    override fun aggregateTotal(): ExecutionStatsSummary {
        val totalExecutions = ExecutionLogsTable.selectAll().count()
        val hitCount =
            ExecutionLogsTable
                .selectAll()
                .where { ExecutionLogsTable.outputDecision eq DECISION_PASS }
                .count()
        val errorCount =
            ExecutionLogsTable
                .selectAll()
                .where { ExecutionLogsTable.status eq STATUS_ERROR }
                .count()
        val avgExpr = ExecutionLogsTable.executionTimeMs.avg()
        val avg: BigDecimal? =
            ExecutionLogsTable
                .select(avgExpr)
                .firstOrNull()
                ?.get(avgExpr)
        return ExecutionStatsSummary(
            totalExecutions = totalExecutions,
            hitCount = hitCount,
            errorCount = errorCount,
            avgExecutionTimeMs = avg?.toDouble() ?: 0.0,
        )
    }

    override fun aggregatePerRule(): List<ExecutionRuleStats> {
        val ruleKey = ExecutionLogsTable.ruleKey
        val countExpr = ExecutionLogsTable.id.count()

        val executionCounts =
            ExecutionLogsTable
                .select(ruleKey, countExpr)
                .groupBy(ruleKey)
                .associate { it[ruleKey] to it[countExpr] }
        val hitCounts =
            ExecutionLogsTable
                .select(ruleKey, countExpr)
                .where { ExecutionLogsTable.outputDecision eq DECISION_PASS }
                .groupBy(ruleKey)
                .associate { it[ruleKey] to it[countExpr] }
        val errorCounts =
            ExecutionLogsTable
                .select(ruleKey, countExpr)
                .where { ExecutionLogsTable.status eq STATUS_ERROR }
                .groupBy(ruleKey)
                .associate { it[ruleKey] to it[countExpr] }
        val avgExpr = ExecutionLogsTable.executionTimeMs.avg()
        val avgTimes =
            ExecutionLogsTable
                .select(ruleKey, avgExpr)
                .groupBy(ruleKey)
                .associate { row ->
                    val avg: BigDecimal? = row[avgExpr]
                    row[ruleKey] to (avg?.toDouble() ?: 0.0)
                }

        return executionCounts.keys
            .sorted()
            .map { key ->
                ExecutionRuleStats(
                    ruleKey = key,
                    executionCount = executionCounts[key] ?: 0L,
                    hitCount = hitCounts[key] ?: 0L,
                    errorCount = errorCounts[key] ?: 0L,
                    avgExecutionTimeMs = avgTimes[key] ?: 0.0,
                )
            }
    }

    /** created_at 降序；id 作次序键保证同秒并列行的确定性顺序 */
    private fun Query.orderByCreatedAtDesc(): Query =
        orderBy(ExecutionLogsTable.createdAt to SortOrder.DESC, ExecutionLogsTable.id to SortOrder.DESC)

    private fun toEntry(row: ResultRow): ExecutionLogEntry =
        ExecutionLogEntry(
            id = row[ExecutionLogsTable.id],
            ruleKey = row[ExecutionLogsTable.ruleKey],
            ruleVersion = row[ExecutionLogsTable.ruleVersion],
            outputDecision = row[ExecutionLogsTable.outputDecision],
            executionTimeMs = row[ExecutionLogsTable.executionTimeMs],
            status = row[ExecutionLogsTable.status],
            errorMessage = row[ExecutionLogsTable.errorMessage],
            // created_at NOT NULL（V5 DDL），理论不可空；EPOCH 兜底防御异常历史行
            createdAt = row[ExecutionLogsTable.createdAt] ?: Instant.EPOCH,
        )
}

/**
 * [CanaryExecutionLogRepository] 的 Exposed 实现（注册方式与代理约束同上）。
 */
class ExposedCanaryExecutionLogRepository : CanaryExecutionLogRepository {
    override fun insertBatch(rows: List<CanaryExecutionLogRow>): Int {
        rows.forEach { row ->
            CanaryExecutionLogTable.insert { statement ->
                statement[traceId] = row.traceId
                statement[targetType] = row.targetType
                statement[targetKey] = row.targetKey
                statement[versionUsed] = row.versionUsed
                statement[isCanary] = row.isCanary
                statement[requestFeatures] = row.requestFeatures
                statement[decisionResult] = row.decisionResult
                statement[executionTimeMs] = row.executionTimeMs
                statement[errorMessage] = row.errorMessage
                statement[createdAt] = row.createdAt
            }
        }
        return rows.size
    }
}
