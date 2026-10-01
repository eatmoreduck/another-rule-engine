package com.eatmoreduck.ruleengine.storage.log

import java.time.Instant

/**
 * 规则执行日志行（列与 V5 一致）。
 *
 * [inputFeatures] 为 JSON 文本（PG 侧 JSONB 列的文本承载口径见 [ExecutionLogsTable] 注释）。
 */
data class ExecutionLogRow(
    val ruleKey: String,
    val ruleVersion: Int?,
    val inputFeatures: String?,
    val outputDecision: String?,
    val outputReason: String?,
    val executionTimeMs: Int?,
    /** SUCCESS / TIMEOUT / ERROR */
    val status: String,
    val errorMessage: String?,
    val createdAt: Instant,
)

/**
 * 灰度执行日志行（列与 V19 一致）。
 */
data class CanaryExecutionLogRow(
    val traceId: String,
    /** RULE / DECISION_FLOW */
    val targetType: String,
    val targetKey: String,
    val versionUsed: Int,
    val isCanary: Boolean,
    val requestFeatures: String?,
    val decisionResult: String?,
    val executionTimeMs: Long?,
    val errorMessage: String?,
    val createdAt: Instant,
)

/**
 * 执行日志读模型（查询面：admin-api 的监控/日志/效果分析接口消费）。
 *
 * 与写入模型 [ExecutionLogRow] 的差异：携带代理主键 [id]（旧契约的日志 id 字段），
 * 且不装载 [ExecutionLogRow.inputFeatures] / [ExecutionLogRow.outputReason] 大字段
 * （现有查询口径均不消费这两列）。
 */
data class ExecutionLogEntry(
    val id: Long,
    val ruleKey: String,
    val ruleVersion: Int?,
    val outputDecision: String?,
    val executionTimeMs: Int?,
    /** SUCCESS / TIMEOUT / ERROR */
    val status: String,
    val errorMessage: String?,
    val createdAt: Instant,
)

/**
 * 全表执行统计汇总（SQL 聚合，不装载明细行；metrics 总览的数据源）。
 *
 * 口径对应旧进程内 Micrometer 计数器（决策节点无状态、指标不可跨实例聚合，
 * 新架构以 execution_logs 为唯一事实源持久化聚合）：
 * - [hitCount]：output_decision = 'PASS'（旧 recordExecution 的命中判定）
 * - [errorCount]：status = 'ERROR'（旧 recordError 计数器）
 */
data class ExecutionStatsSummary(
    val totalExecutions: Long,
    val hitCount: Long,
    val errorCount: Long,
    /** 平均执行耗时（毫秒）；无有效耗时行时为 0 */
    val avgExecutionTimeMs: Double,
)

/** 按规则分组的执行统计（metrics 规则排行的数据源；无日志的规则不出现在结果中） */
data class ExecutionRuleStats(
    val ruleKey: String,
    val executionCount: Long,
    val hitCount: Long,
    val errorCount: Long,
    /** 平均执行耗时（毫秒）；该规则无有效耗时行时为 0 */
    val avgExecutionTimeMs: Double,
)

/**
 * 规则执行日志仓储：决策链路异步批量写入的落库入口 + admin-api 的查询面。
 *
 * 事务边界由调用方提供（decision-api 的刷盘协程内 `transaction { }`、admin-api 的
 * Spring `@Transactional` 或测试事务），本接口不开事务。
 */
interface ExecutionLogRepository {
    /**
     * 批量插入执行日志，返回成功写入行数。
     * 任一行失败则整批回滚（调用方决定重试或丢弃）。
     */
    fun insertBatch(rows: List<ExecutionLogRow>): Int

    /**
     * 查询指定规则的全部执行日志（created_at 降序）。
     * 查询语义照搬旧 ExecutionLogRepository.findByRuleKeyOrderByCreatedAtDesc。
     */
    fun findLogsByRuleKey(ruleKey: String): List<ExecutionLogEntry>

    /** 查询指定规则在时间闭区间 [start, end] 内的执行日志（created_at 降序） */
    fun findLogsByRuleKeyAndTimeRange(
        ruleKey: String,
        start: Instant,
        end: Instant,
    ): List<ExecutionLogEntry>

    /**
     * 按状态查询执行日志（created_at 降序）。
     * 查询语义照搬旧 findByStatusOrderByCreatedAtDesc。
     */
    fun findLogsByStatus(status: String): List<ExecutionLogEntry>

    /**
     * 最近执行日志（created_at 降序，最多 [limit] 条）。
     * 查询语义照搬旧 findTop100ByOrderByCreatedAtDesc（limit 由调用方传 100）。
     */
    fun findRecentLogs(limit: Int): List<ExecutionLogEntry>

    /** 时间闭区间 [start, end] 内的全部执行日志（created_at 降序；分析概览的全量来源） */
    fun findLogsByTimeRange(
        start: Instant,
        end: Instant,
    ): List<ExecutionLogEntry>

    /** 全表执行统计汇总（SQL 聚合） */
    fun aggregateTotal(): ExecutionStatsSummary

    /** 按 rule_key 分组的执行统计（SQL 聚合，execution_count 降序） */
    fun aggregatePerRule(): List<ExecutionRuleStats>
}

/**
 * 灰度执行日志仓储（写入面：决策侧异步批量写；查询面属 admin-api 分析域，不在本批）。
 */
interface CanaryExecutionLogRepository {
    /** 批量插入灰度执行日志，返回成功写入行数。 */
    fun insertBatch(rows: List<CanaryExecutionLogRow>): Int
}
