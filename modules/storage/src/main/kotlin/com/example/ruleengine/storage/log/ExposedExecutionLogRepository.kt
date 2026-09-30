package com.example.ruleengine.storage.log

import org.jetbrains.exposed.v1.jdbc.insert

/**
 * [ExecutionLogRepository] 的 Exposed 实现：单事务逐行 insert（PG 端经
 * JDBC 批量优化后仍是同事务原子提交，量级为每次刷盘数十~数百行，足够）。
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
