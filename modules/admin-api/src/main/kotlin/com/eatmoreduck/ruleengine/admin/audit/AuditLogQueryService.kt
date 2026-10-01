package com.eatmoreduck.ruleengine.admin.audit

import com.eatmoreduck.ruleengine.admin.dto.AuditLogFilter
import com.eatmoreduck.ruleengine.admin.dto.AuditLogResponse
import com.eatmoreduck.ruleengine.admin.dto.PageResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 审计日志查询服务（本批只读：查询接口对齐旧 AuditLogController 的三个端点，
 * 分页在内存完成，口径与 2b PageResponse 一致）。
 */
@Service
class AuditLogQueryService(
    private val repository: AuditLogRepository,
) {
    /** 实体审计历史（operation_time 降序） */
    @Transactional(readOnly = true)
    fun getAuditHistory(
        entityType: String,
        entityId: String,
    ): List<AuditLogResponse> = repository.findByEntity(entityType, entityId).map(::toResponse)

    /**
     * 操作人活动记录：时间缺省兜底最近 7 天（与旧 Controller 的默认值一致）。
     */
    @Transactional(readOnly = true)
    fun getOperatorActivity(
        operator: String,
        start: LocalDateTime?,
        end: LocalDateTime?,
    ): List<AuditLogResponse> {
        val zone = ZoneId.systemDefault()
        val startInstant = start?.atZone(zone)?.toInstant() ?: Instant.now().minus(DEFAULT_LOOKBACK)
        val endInstant = end?.atZone(zone)?.toInstant() ?: Instant.now()
        return repository.findByOperatorAndTimeRange(operator, startInstant, endInstant).map(::toResponse)
    }

    /** 综合分页查询（operator 模糊 / entityType、operation 精确 / 时间闭区间） */
    @Transactional(readOnly = true)
    fun queryAuditLogs(
        filter: AuditLogFilter,
        page: Int,
        size: Int,
    ): PageResponse<AuditLogResponse> {
        val zone = ZoneId.systemDefault()
        val rows =
            repository.findByConditions(
                operator = filter.operator,
                entityType = filter.entityType,
                operation = filter.operation,
                startTime = filter.startTime?.atZone(zone)?.toInstant(),
                endTime = filter.endTime?.atZone(zone)?.toInstant(),
            )
        return PageResponse.of(rows.map(::toResponse), page, size)
    }

    private fun toResponse(row: AuditLogRow): AuditLogResponse =
        AuditLogResponse(
            id = row.id,
            entityType = row.entityType,
            entityId = row.entityId,
            operation = row.operation,
            operationDetail = row.operationDetail,
            operator = row.operator,
            operatorIp = row.operatorIp,
            operationTime = row.operationTime,
            status = row.status,
            errorMessage = row.errorMessage,
            requestId = row.requestId,
        )

    companion object {
        /** 操作人活动记录的默认回看窗口（旧 Controller 兜底 7 天） */
        private val DEFAULT_LOOKBACK = java.time.Duration.ofDays(7)
    }
}
