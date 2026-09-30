package com.example.ruleengine.admin

import com.example.ruleengine.admin.dto.CreateNameListEntryRequest
import com.example.ruleengine.admin.namelist.NameListService
import com.example.ruleengine.shared.cache.CacheInvalidationEvent
import com.example.ruleengine.shared.cache.CacheInvalidationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * 黑白名单服务单元测试（内存仓储）。
 * 覆盖：listKey 缺省 GLOBAL、expiredAt ISO 解析（含非法格式）、业务键查重、
 * 递进过滤查询、list-keys 去重、批量导入聚合、删除语义。
 */
@DisplayName("黑白名单服务")
class NameListServiceTest {
    private lateinit var repository: FakeNameListRepository
    private lateinit var eventPublisher: RecordingEventPublisher
    private lateinit var service: NameListService

    @BeforeEach
    fun setUp() {
        repository = FakeNameListRepository()
        eventPublisher = RecordingEventPublisher()
        service = NameListService(repository, eventPublisher)
    }

    private fun request(
        listType: String = "BLACK",
        listKey: String? = null,
        keyType: String = "IP",
        keyValue: String = "1.2.3.4",
        expiredAt: String? = null,
    ) = CreateNameListEntryRequest(
        listType = listType,
        listKey = listKey,
        keyType = keyType,
        keyValue = keyValue,
        reason = "欺诈订单",
        source = "人工录入",
        expiredAt = expiredAt,
    )

    @Test
    fun `创建条目缺省 listKey 落 GLOBAL`() {
        val response = service.createEntry(request(), "tester")

        assertEquals("GLOBAL", response.listKey)
        assertEquals("BLACK", response.listType)
        assertEquals("IP", response.keyType)
        assertEquals("1.2.3.4", response.keyValue)
        assertNull(response.expiredAt)
        assertEquals("tester", response.createdBy)
        assertNull(response.updatedBy)
    }

    @Test
    fun `listKey 可传决策流键做流级隔离`() {
        val response = service.createEntry(request(listKey = "flow_a"), "tester")
        assertEquals("flow_a", response.listKey)
        // 空白 listKey 同样兜底 GLOBAL（照旧 isBlank 判断）
        assertEquals("GLOBAL", service.createEntry(request(listKey = "  "), "tester").listKey)
    }

    @Test
    fun `expiredAt ISO 解析与非法格式拒绝`() {
        val response = service.createEntry(request(keyValue = "9.9.9.9", expiredAt = "2030-01-01T10:30:00"), "tester")
        assertTrue(response.expiredAt!!.isAfter(Instant.parse("2029-12-31T00:00:00Z")))
        // 空白视同未填
        assertNull(service.createEntry(request(keyValue = "9.9.9.8", expiredAt = "  "), "tester").expiredAt)

        val bad =
            assertThrows<IllegalArgumentException> {
                service.createEntry(request(expiredAt = "not-a-date"), "tester")
            }
        assertTrue(bad.message!!.startsWith("expiredAt 格式非法"), bad.message)
    }

    @Test
    fun `重复业务键创建被拒绝`() {
        service.createEntry(request(), "tester")

        val dup = assertThrows<IllegalArgumentException> { service.createEntry(request(), "tester") }
        assertTrue(dup.message!!.startsWith("名单条目已存在"), dup.message)

        // 不同 listKey 同键值不冲突
        val isolated = service.createEntry(request(listKey = "flow_a"), "tester")
        assertEquals("flow_a", isolated.listKey)
    }

    @Test
    fun `递进过滤查询与 list-keys 去重`() {
        service.createEntry(request(), "tester")
        service.createEntry(request(listKey = "flow_a"), "tester")
        service.createEntry(request(listKey = "flow_a", keyType = "DEVICE_ID", keyValue = "d1"), "tester")
        service.createEntry(request(listType = "WHITE", keyValue = "5.6.7.8"), "tester")

        assertEquals(4, service.listEntries(null, null, null, 0, 20).totalElements)
        assertEquals(2, service.listEntries("flow_a", null, null, 0, 20).totalElements)
        assertEquals(2, service.listEntries("flow_a", "BLACK", null, 0, 20).totalElements)
        assertEquals(1, service.listEntries("flow_a", "BLACK", "DEVICE_ID", 0, 20).totalElements)
        assertEquals(0, service.listEntries("flow_a", "WHITE", null, 0, 20).totalElements)

        assertEquals(listOf("GLOBAL", "flow_a"), service.getDistinctListKeys())
    }

    @Test
    fun `批量导入聚合导入与跳过`() {
        val result =
            service.importEntries(
                listOf(
                    request(keyValue = "1.1.1.1"),
                    // 重复条目 → 跳过
                    request(keyValue = "1.1.1.1"),
                    // 非法过期时间 → 跳过
                    request(keyValue = "2.2.2.2", expiredAt = "bad"),
                    request(keyValue = "3.3.3.3"),
                ),
                "tester",
            )

        assertEquals(2, result.importedCount)
        assertEquals(2, result.skippedCount)
        assertEquals(2, result.skipReasons.size)
        assertEquals(2, repository.entries.size)
    }

    @Test
    fun `删除与详情语义`() {
        val created = service.createEntry(request(), "tester")

        assertEquals(created.id, service.getEntry(created.id)!!.id)
        assertNull(service.getEntry(999L))

        service.deleteEntry(created.id)
        assertNull(service.getEntry(created.id))
        // 删除不存在条目静默成功（JPA deleteById 行为）
        service.deleteEntry(999L)

        // 阶段 5：新增与删除各广播一则名单失效事件（删除不存在条目不广播）
        val invalidations = eventPublisher.published<CacheInvalidationEvent>()
        assertEquals(2, invalidations.size)
        assertTrue(invalidations.all { it.type == CacheInvalidationType.NAME_LIST && it.key == "GLOBAL" })
    }
}
