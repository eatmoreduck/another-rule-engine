package com.eatmoreduck.ruleengine.admin.dto

import java.time.Instant

/**
 * 指定规则的执行统计（对应旧 ExecutionStats DTO：MON-01 单规则命中统计）。
 *
 * 数据源差异说明：旧实现读取 admin 进程内的 Micrometer 计数器/计时器
 * （重启清零、多实例不可聚合）；新架构决策节点无状态，本 DTO 由 execution_logs
 * 聚合得出（全量持久化口径），字段集与旧 JSON 一致。
 */
data class ExecutionStatsResponse(
    val totalExecutions: Long,
    val hitCount: Long,
    val errorCount: Long,
    val avgExecutionTimeMs: Double,
    /** P95 执行耗时（毫秒）：旧实现为 Micrometer 直方图近似值，此处为日志明细的最近邻分位 */
    val p95ExecutionTimeMs: Double,
    /** 最后执行时间；该规则无执行记录时为 null（与旧进程内 Map 未命中返回 null 一致） */
    val lastExecutedAt: Instant?,
)
