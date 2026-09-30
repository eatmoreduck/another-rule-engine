package com.example.ruleengine.admin

import com.example.ruleengine.admin.audit.AuditLogQueryService
import com.example.ruleengine.admin.audit.AuditLogRow
import com.example.ruleengine.admin.dto.AuditLogFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 审计日志查询服务单元测试（内存仓储）。
 * 覆盖：实体历史降序、操作人活动窗口（含默认 7 天）、综合查询的
 * operator 模糊 / entityType、operation 精确 / 时间闭区间、分页口径。
 */
@DisplayName("审计日志查询服务")
class AuditLogQueryServiceTest {
    private lateinit var repository: FakeAuditLogRepository
    private lateinit var service: AuditLogQueryService

    private val base = Instant.parse("2026-01-10T00:00:00Z")

    @BeforeEach
    fun setUp() {
        repository = FakeAuditLogRepository()
        service = AuditLogQueryService(repository)
        seed("RULE", "r1", "RULE_CREATE", "alice", base.plusSeconds(100))
        seed("RULE", "r1", "RULE_UPDATE", "bob", base.plusSeconds(200))
        seed("DECISION_FLOW", "flow_a", "FLOW_CREATE", "alice", base.plusSeconds(300))
        seed("DECISION_FLOW", "flow_a", "FLOW_DELETE", "carol", base.plusSeconds(400))
        seed("DECISION_FLOW", "flow_a", "FLOW_ENABLE", "Alice", base.plusSeconds(500))
    }

    private fun seed(
        entityType: String,
        entityId: String,
        operation: String,
        operator: String,
        at: Instant,
    ) {
        repository.seed(
            AuditLogRow(
                id = 0L,
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                operationDetail = null,
                operator = operator,
                operatorIp = "127.0.0.1",
                operationTime = at,
                status = "SUCCESS",
                errorMessage = null,
                requestId = null,
            ),
        )
    }

    @Test
    fun `实体历史按时间降序`() {
        val history = service.getAuditHistory("DECISION_FLOW", "flow_a")

        assertEquals(3, history.size)
        assertEquals("FLOW_ENABLE", history[0].operation)
        assertEquals("FLOW_CREATE", history[2].operation)
    }

    @Test
    fun `操作人活动时间窗口与默认回看`() {
        val zone = ZoneId.systemDefault()
        val start = LocalDateTime.ofInstant(base.plusSeconds(150), zone)
        val end = LocalDateTime.ofInstant(base.plusSeconds(350), zone)

        // alice 精确匹配（大小写敏感，照旧 equals 过滤）+ 闭区间
        val activity = service.getOperatorActivity("alice", start, end)
        assertEquals(1, activity.size)
        assertEquals("FLOW_CREATE", activity[0].operation)

        // end 早于全部记录 → 空
        assertTrue(service.getOperatorActivity("alice", start, LocalDateTime.ofInstant(base, zone)).isEmpty())
    }

    @Test
    fun `综合查询过滤组合与分页`() {
        // operator 模糊（LIKE %..%，大小写由数据库 collation 决定；内存实现 contains 为区分大小写近似）
        assertEquals(2, service.queryAuditLogs(AuditLogFilter(operator = "ali"), 0, 20).totalElements)
        // entityType 精确
        assertEquals(3, service.queryAuditLogs(AuditLogFilter(entityType = "DECISION_FLOW"), 0, 20).totalElements)
        // operation 精确
        assertEquals(1, service.queryAuditLogs(AuditLogFilter(operation = "RULE_CREATE"), 0, 20).totalElements)
        // 时间闭区间
        val zone = ZoneId.systemDefault()
        assertEquals(
            2,
            service
                .queryAuditLogs(
                    filter =
                        AuditLogFilter(
                            startTime = LocalDateTime.ofInstant(base.plusSeconds(150), zone),
                            endTime = LocalDateTime.ofInstant(base.plusSeconds(350), zone),
                        ),
                    page = 0,
                    size = 20,
                ).totalElements,
        )
        // 组合
        assertEquals(
            1,
            service
                .queryAuditLogs(
                    filter = AuditLogFilter(entityType = "DECISION_FLOW", operation = "FLOW_DELETE"),
                    page = 0,
                    size = 20,
                ).totalElements,
        )
        // 无过滤全量
        assertEquals(5, service.queryAuditLogs(AuditLogFilter(), 0, 20).totalElements)

        // 分页切片
        val page = service.queryAuditLogs(AuditLogFilter(), 1, 2)
        assertEquals(5, page.totalElements)
        assertEquals(3, page.totalPages)
        assertEquals(2, page.content.size)
    }
}
