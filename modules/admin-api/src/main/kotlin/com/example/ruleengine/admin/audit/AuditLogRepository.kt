package com.example.ruleengine.admin.audit

import com.example.ruleengine.admin.data.AuditLogsTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 审计日志行模型（只读；列与 V3 迁移一致） */
data class AuditLogRow(
    val id: Long,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val operationDetail: String?,
    val operator: String,
    val operatorIp: String?,
    val operationTime: Instant,
    val status: String,
    val errorMessage: String?,
    val requestId: String?,
)

/**
 * 审计日志数据访问（只读：本批仅实现查询侧，写入侧由旧 @Auditable 切面的
 * 后续替代方案统一规划，见汇报）。
 *
 * 查询语义照搬旧 AuditLogRepository 的 JPQL：
 * - 实体历史：entityType + entityId 精确匹配，operation_time 降序；
 * - 分页综合查询：operator 为 LIKE %..% 模糊，entityType/operation 精确，
 *   时间为闭区间 [start, end]。
 */
interface AuditLogRepository {
    fun findByEntity(
        entityType: String,
        entityId: String,
    ): List<AuditLogRow>

    /** 操作人活动查询：operator 精确匹配 + 时间区间（旧实现在内存二次过滤，此处下推为等价 SQL） */
    fun findByOperatorAndTimeRange(
        operator: String,
        start: Instant,
        end: Instant,
    ): List<AuditLogRow>

    /** 综合分页查询（operator 模糊、entityType/operation 精确、时间闭区间），operation_time 降序 */
    fun findByConditions(
        operator: String?,
        entityType: String?,
        operation: String?,
        startTime: Instant?,
        endTime: Instant?,
    ): List<AuditLogRow>
}

/** [AuditLogRepository] 的 Exposed 实现：直读 audit_logs 表（列定义见 [AuditLogsTable]） */
@Repository
@Transactional(readOnly = true)
class ExposedAuditLogRepository : AuditLogRepository {
    private val table = AuditLogsTable

    override fun findByEntity(
        entityType: String,
        entityId: String,
    ): List<AuditLogRow> =
        table
            .selectAll()
            .where { (table.entityType eq entityType) and (table.entityId eq entityId) }
            .orderBy(table.operationTime to SortOrder.DESC)
            .map(::toRow)

    override fun findByOperatorAndTimeRange(
        operator: String,
        start: Instant,
        end: Instant,
    ): List<AuditLogRow> =
        table
            .selectAll()
            .where {
                (table.operator eq operator) and
                    (table.operationTime greaterEq start) and
                    (table.operationTime lessEq end)
            }.orderBy(table.operationTime to SortOrder.DESC)
            .map(::toRow)

    override fun findByConditions(
        operator: String?,
        entityType: String?,
        operation: String?,
        startTime: Instant?,
        endTime: Instant?,
    ): List<AuditLogRow> {
        // operator 为 LIKE %..% 模糊（照旧 JPQL），其余精确；旧实现由数据库排序分页，此处取确定性降序
        val conditions =
            listOfNotNull(
                operator?.takeIf { it.isNotEmpty() }?.let { table.operator.like("%$it%") },
                entityType?.takeIf { it.isNotEmpty() }?.let { table.entityType eq it },
                operation?.takeIf { it.isNotEmpty() }?.let { table.operation eq it },
                startTime?.let { table.operationTime greaterEq it },
                endTime?.let { table.operationTime lessEq it },
            )
        val query = table.selectAll()
        if (conditions.isNotEmpty()) {
            query.where { conditions.reduce { a, b -> a and b } }
        }
        return query.orderBy(table.operationTime to SortOrder.DESC, table.id to SortOrder.DESC).map(::toRow)
    }

    private fun toRow(row: ResultRow): AuditLogRow =
        AuditLogRow(
            id = row[table.id],
            entityType = row[table.entityType],
            entityId = row[table.entityId],
            operation = row[table.operation],
            operationDetail = row[table.operationDetail],
            operator = row[table.operator],
            operatorIp = row[table.operatorIp],
            // operation_time 理论上非空（DEFAULT CURRENT_TIMESTAMP），EPOCH 兜底防异常行
            operationTime = row[table.operationTime] ?: Instant.EPOCH,
            status = row[table.status],
            errorMessage = row[table.errorMessage],
            requestId = row[table.requestId],
        )
}
