package com.example.ruleengine.storage.log

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
 * 规则执行日志仓储：决策链路异步批量写入的落库入口。
 *
 * 事务边界由调用方提供（decision-api 的刷盘协程内 `transaction { }` 或测试事务），
 * 本接口不开事务。
 */
interface ExecutionLogRepository {
    /**
     * 批量插入执行日志，返回成功写入行数。
     * 任一行失败则整批回滚（调用方决定重试或丢弃）。
     */
    fun insertBatch(rows: List<ExecutionLogRow>): Int
}

/**
 * 灰度执行日志仓储（写入面：决策侧异步批量写；查询面属 admin-api 分析域，不在本批）。
 */
interface CanaryExecutionLogRepository {
    /** 批量插入灰度执行日志，返回成功写入行数。 */
    fun insertBatch(rows: List<CanaryExecutionLogRow>): Int
}
