package com.eatmoreduck.ruleengine.admin.monitoring

import com.eatmoreduck.ruleengine.admin.dto.ExecutionLogResponse
import com.eatmoreduck.ruleengine.storage.log.ExecutionLogEntry
import com.eatmoreduck.ruleengine.storage.log.ExecutionLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 规则执行日志查询服务（对应旧 ExecutionLogService 的查询职责）。
 *
 * 查询语义逐条照搬旧实现：
 * - 最近日志：先取最近 100 条（created_at 降序），再按级别过滤，最后截断 limit；
 * - 按规则查询：start 与 end 同时提供才走时间窗，否则全量；
 * - 响应字段映射（result/level/ruleName）照搬旧 ExecutionLogResponse.fromEntity。
 */
@Service
class ExecutionLogQueryService(
    private val executionLogRepository: ExecutionLogRepository,
) {
    /** 最近执行日志（旧 findTop100ByOrderByCreatedAtDesc + 内存过滤/截断） */
    @Transactional(readOnly = true)
    fun getRecentLogs(
        limit: Int,
        level: String?,
    ): List<ExecutionLogResponse> {
        val responses = executionLogRepository.findRecentLogs(RECENT_WINDOW).map(::toResponse)
        val filtered =
            if (!level.isNullOrBlank()) {
                responses.filter { level.equals(it.level, ignoreCase = true) }
            } else {
                responses
            }
        return if (limit > 0 && filtered.size > limit) filtered.subList(0, limit) else filtered
    }

    /** 指定规则的执行日志（时间窗为可选；created_at 降序） */
    @Transactional(readOnly = true)
    fun getLogsByRuleKey(
        ruleKey: String,
        start: LocalDateTime?,
        end: LocalDateTime?,
    ): List<ExecutionLogResponse> {
        val zone = ZoneId.systemDefault()
        return if (start != null && end != null) {
            executionLogRepository.findLogsByRuleKeyAndTimeRange(
                ruleKey = ruleKey,
                start = start.atZone(zone).toInstant(),
                end = end.atZone(zone).toInstant(),
            )
        } else {
            executionLogRepository.findLogsByRuleKey(ruleKey)
        }.map(::toResponse)
    }

    /** 按状态查询执行日志（SUCCESS/TIMEOUT/ERROR；created_at 降序） */
    @Transactional(readOnly = true)
    fun getLogsByStatus(status: String): List<ExecutionLogResponse> = executionLogRepository.findLogsByStatus(status).map(::toResponse)

    /** 行 → 响应 DTO（映射口径照搬旧 ExecutionLogResponse.fromEntity） */
    private fun toResponse(entry: ExecutionLogEntry): ExecutionLogResponse =
        ExecutionLogResponse(
            id = entry.id,
            ruleKey = entry.ruleKey,
            // 旧实现：实体无 ruleName 字段，用 ruleKey 代替
            ruleName = entry.ruleKey,
            result =
                when {
                    entry.outputDecision.equals("PASS", ignoreCase = true) -> "HIT"
                    entry.status == "ERROR" -> "ERROR"
                    else -> "MISS"
                },
            executionTime = entry.executionTimeMs,
            level =
                when (entry.status) {
                    "SUCCESS" -> "INFO"
                    "TIMEOUT" -> "WARN"
                    "ERROR" -> "ERROR"
                    else -> "INFO"
                },
            errorMessage = entry.errorMessage,
            executedAt = entry.createdAt,
        )

    companion object {
        /** 最近日志的取数窗口（旧 findTop100... 的 100 条口径） */
        private const val RECENT_WINDOW: Int = 100
    }
}
