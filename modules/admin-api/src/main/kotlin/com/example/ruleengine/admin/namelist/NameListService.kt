package com.example.ruleengine.admin.namelist

import com.example.ruleengine.admin.dto.CreateNameListEntryRequest
import com.example.ruleengine.admin.dto.NameListEntryResponse
import com.example.ruleengine.admin.dto.NameListImportResult
import com.example.ruleengine.admin.dto.PageResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 黑白名单管理服务：单条 CRUD + 批量导入。
 *
 * 业务规则照搬旧 NameListService：
 * - listKey 缺省兜底 GLOBAL（全局共享），可传决策流 flowKey 做流级隔离；
 * - expiredAt 为 ISO-8601 本地日期时间字符串，空串视同未填；
 * - list 过滤为 listKey/listType/keyType 递进组合；
 * - list-keys 返回全部不重复的 listKey。
 *
 * 与旧实现的差异（见汇报）：新增创建前的业务键查重（旧实现直插，唯一约束冲突落
 * 兜底 500），重复条目收敛为 400 业务错误，避免约束异常泄漏。
 */
@Service
class NameListService(
    private val repository: NameListRepository,
) {
    /** 单条创建（解析语义照旧：listKey 默认 GLOBAL、expiredAt ISO 解析） */
    @Transactional
    fun createEntry(
        request: CreateNameListEntryRequest,
        operator: String,
    ): NameListEntryResponse {
        val listKey = resolveListKey(request)
        val expiredAt = parseExpiredAt(request.expiredAt)
        if (repository.existsByBusinessKey(listKey, request.listType, request.keyType, request.keyValue)) {
            throw IllegalArgumentException(
                "名单条目已存在: listKey=$listKey, listType=${request.listType}, " +
                    "keyType=${request.keyType}, keyValue=${request.keyValue}",
            )
        }
        val saved =
            repository.insert(
                NameListEntryRow(
                    id = 0L,
                    listType = request.listType,
                    keyType = request.keyType,
                    keyValue = request.keyValue,
                    listKey = listKey,
                    reason = request.reason,
                    source = request.source,
                    expiredAt = expiredAt,
                    createdBy = operator,
                    createdAt = Instant.now(),
                    updatedBy = null,
                    updatedAt = null,
                ),
            )
        log.info("添加名单条目: listKey={}, {} {} {}", saved.listKey, saved.listType, saved.keyType, saved.keyValue)
        return toResponse(saved)
    }

    /** 批量导入：单事务内逐条复用单条解析语义，重复/非法条目跳过并记录原因 */
    @Transactional
    fun importEntries(
        requests: List<CreateNameListEntryRequest>,
        operator: String,
    ): NameListImportResult {
        var imported = 0
        val skipReasons = mutableListOf<String>()
        for (request in requests) {
            try {
                createEntry(request, operator)
                imported++
            } catch (e: IllegalArgumentException) {
                skipReasons.add(e.message ?: "未知原因")
            }
        }
        log.info("名单批量导入: 总数={}, 导入={}, 跳过={}", requests.size, imported, skipReasons.size)
        return NameListImportResult(
            importedCount = imported,
            skippedCount = skipReasons.size,
            skipReasons = skipReasons,
        )
    }

    /** 单条详情；不存在返回 null（控制器映射为旧契约的 404 空体） */
    @Transactional(readOnly = true)
    fun getEntry(id: Long): NameListEntryResponse? = repository.findById(id)?.let(::toResponse)

    /** 分页列表（listKey/listType/keyType 递进过滤，照旧） */
    @Transactional(readOnly = true)
    fun listEntries(
        listKey: String?,
        listType: String?,
        keyType: String?,
        page: Int,
        size: Int,
    ): PageResponse<NameListEntryResponse> =
        PageResponse.of(
            repository.search(listKey, listType, keyType).map(::toResponse),
            page,
            size,
        )

    /** 全部不重复的 listKey */
    @Transactional(readOnly = true)
    fun getDistinctListKeys(): List<String> = repository.findDistinctListKeys()

    /** 删除条目（物理删除，照旧；目标不存在时静默成功，与 JPA deleteById 行为一致） */
    @Transactional
    fun deleteEntry(id: Long) {
        repository.deleteById(id)
        log.info("删除名单条目: id={}", id)
    }

    // ---------- 私有辅助 ----------

    private fun resolveListKey(request: CreateNameListEntryRequest): String =
        request.listKey
            ?.takeIf { it.isNotBlank() }
            ?: GLOBAL_LIST_KEY

    /** expiredAt ISO 本地日期时间解析（空串/空白视同未填；非法格式拒绝为 400） */
    private fun parseExpiredAt(raw: String?): Instant? {
        val value = raw?.takeIf { it.isNotBlank() } ?: return null
        return try {
            LocalDateTime
                .parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault())
                .toInstant()
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("expiredAt 格式非法（应为 ISO-8601 本地日期时间）: $value", e)
        }
    }

    private fun toResponse(row: NameListEntryRow): NameListEntryResponse =
        NameListEntryResponse(
            id = row.id,
            listType = row.listType,
            keyType = row.keyType,
            keyValue = row.keyValue,
            listKey = row.listKey,
            reason = row.reason,
            source = row.source,
            expiredAt = row.expiredAt,
            createdBy = row.createdBy,
            createdAt = row.createdAt,
            updatedBy = row.updatedBy,
            updatedAt = row.updatedAt,
        )

    companion object {
        private val log = LoggerFactory.getLogger(NameListService::class.java)

        /** 全局共享名单键（旧实现缺省值） */
        const val GLOBAL_LIST_KEY = "GLOBAL"
    }
}
