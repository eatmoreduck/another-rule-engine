package com.eatmoreduck.ruleengine.storage.log

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

/**
 * 执行日志表对象：映射 V5 建表（execution_logs）。
 *
 * 决策日志被 decision-api（批量写入）与 admin-api（分析查询）共享，
 * 仓储统一放在 storage 层作为单一事实源。其余约定同 [StorageTablesKt]。
 *
 * 注意：`input_features` 在 PG 基线中是 JSONB 列，本表对象以文本承载
 * （写入路径要求 JDBC URL 携带 `stringtype=unspecified`，由 PG 服务端把
 * VARCHAR 参数隐式转换为目标 JSONB 类型——PG JDBC 官方推荐做法；
 * 读取路径 rs.getString 对 JSONB 列返回原文本）。这是 PG 与 H2 测试同构的
 * 最小代价方案，真实 schema 事实标准仍是 Flyway 迁移脚本。
 */
object ExecutionLogsTable : Table("execution_logs") {
    val id = long("id").autoIncrement()
    val ruleKey = varchar("rule_key", 255)
    val ruleVersion = integer("rule_version").nullable()
    val inputFeatures = text("input_features").nullable()
    val outputDecision = varchar("output_decision", 50).nullable()
    val outputReason = text("output_reason").nullable()
    val executionTimeMs = integer("execution_time_ms").nullable()
    val status = varchar("status", 20)

    /** DDL DEFAULT 'SUCCESS' 无 NOT NULL：可空以防御历史行，写入路径恒显式赋值 */
    val errorMessage = text("error_message").nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * 灰度执行日志表对象：映射 V19 建表（canary_execution_log）。
 *
 * `request_features` 同为 JSONB 列，文本承载口径见 [ExecutionLogsTable] 注释。
 */
object CanaryExecutionLogTable : Table("canary_execution_log") {
    val id = long("id").autoIncrement()
    val traceId = varchar("trace_id", 64)
    val targetType = varchar("target_type", 20)
    val targetKey = varchar("target_key", 255)
    val versionUsed = integer("version_used")
    val isCanary = bool("is_canary")
    val requestFeatures = text("request_features").nullable()
    val decisionResult = varchar("decision_result", 50).nullable()
    val executionTimeMs = long("execution_time_ms").nullable()
    val errorMessage = text("error_message").nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
