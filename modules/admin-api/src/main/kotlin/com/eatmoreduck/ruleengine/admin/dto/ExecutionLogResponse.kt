package com.eatmoreduck.ruleengine.admin.dto

import java.time.Instant

/**
 * 执行日志响应 DTO（对应旧 ExecutionLogResponse）。
 *
 * 字段映射口径照搬旧 fromEntity：
 * - [result]：output_decision=PASS → HIT；status=ERROR → ERROR；其余 → MISS
 * - [level]：status SUCCESS→INFO / TIMEOUT→WARN / ERROR→ERROR / 其他→INFO
 * - [ruleName]：旧实体无 ruleName 字段，用 ruleKey 代替
 * - [executionTime]：execution_time_ms
 * - [executedAt]：created_at
 */
data class ExecutionLogResponse(
    val id: Long,
    val ruleKey: String,
    val ruleName: String,
    /** HIT / MISS / ERROR */
    val result: String,
    /** 执行耗时（毫秒） */
    val executionTime: Int?,
    /** INFO / WARN / ERROR */
    val level: String,
    /** 错误信息 */
    val errorMessage: String?,
    val executedAt: Instant?,
)
