package com.eatmoreduck.ruleengine.admin.namelist

import com.eatmoreduck.ruleengine.admin.data.NameListTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 名单条目行模型（列与 V11/V12/V13 迁移一致，list_key 为 V12 引入的流级隔离键） */
data class NameListEntryRow(
    val id: Long,
    val listType: String,
    val keyType: String,
    val keyValue: String,
    val listKey: String,
    val reason: String?,
    val source: String?,
    val expiredAt: Instant?,
    val createdBy: String,
    val createdAt: Instant,
    val updatedBy: String?,
    val updatedAt: Instant?,
)

/**
 * 黑白名单数据访问：管理端 CRUD 与批量导入。
 *
 * 查询语义照搬旧 NameListRepository 的派生查询组合（listKey/listType/keyType
 * 递进过滤），排序约定为 id 升序（旧 JPA findAll 无排序，此处取确定性顺序）。
 * 决策执行侧的热路径存在性查询（existsInList）属 decision-api 职责，不在本接口。
 */
interface NameListRepository {
    fun insert(entry: NameListEntryRow): NameListEntryRow

    fun findById(id: Long): NameListEntryRow?

    fun existsByBusinessKey(
        listKey: String,
        listType: String,
        keyType: String,
        keyValue: String,
    ): Boolean

    /** 递进过滤组合查询（listKey/listType/keyType 逐级生效，照旧） */
    fun search(
        listKey: String?,
        listType: String?,
        keyType: String?,
    ): List<NameListEntryRow>

    /** 全部不重复的 listKey（升序） */
    fun findDistinctListKeys(): List<String>

    fun deleteById(id: Long): Int
}

/** [NameListRepository] 的 Exposed 实现：直读 name_list 表（列定义见 [NameListTable]） */
@Repository
@Transactional(readOnly = true)
class ExposedNameListRepository : NameListRepository {
    private val table = NameListTable

    @Transactional
    override fun insert(entry: NameListEntryRow): NameListEntryRow {
        table.insert { statement ->
            statement[listType] = entry.listType
            statement[keyType] = entry.keyType
            statement[keyValue] = entry.keyValue
            statement[listKey] = entry.listKey
            statement[reason] = entry.reason
            statement[sourceCol] = entry.source
            statement[expiredAt] = entry.expiredAt
            statement[createdBy] = entry.createdBy
            statement[createdAt] = entry.createdAt
            statement[updatedBy] = entry.updatedBy
            statement[updatedAt] = entry.updatedAt
        }
        return findLatest(entry.listKey, entry.listType, entry.keyType, entry.keyValue)
            ?: throw IllegalStateException("名单条目插入后回读失败: ${entry.listKey}/${entry.keyValue}")
    }

    override fun findById(id: Long): NameListEntryRow? =
        table
            .selectAll()
            .where { table.id eq id }
            .singleOrNull()
            ?.let(::toRow)

    override fun existsByBusinessKey(
        listKey: String,
        listType: String,
        keyType: String,
        keyValue: String,
    ): Boolean =
        table
            .selectAll()
            .where {
                (table.listKey eq listKey) and
                    (table.listType eq listType) and
                    (table.keyType eq keyType) and
                    (table.keyValue eq keyValue)
            }.any()

    override fun search(
        listKey: String?,
        listType: String?,
        keyType: String?,
    ): List<NameListEntryRow> {
        // 递进过滤组合（旧派生查询的语义：提供的条件全部生效）
        val conditions =
            listOfNotNull(
                listKey?.let { table.listKey eq it },
                listType?.let { table.listType eq it },
                keyType?.let { table.keyType eq it },
            )
        val query = table.selectAll()
        if (conditions.isNotEmpty()) {
            query.where { conditions.reduce { a, b -> a and b } }
        }
        return query.orderBy(table.id to SortOrder.ASC).map(::toRow)
    }

    override fun findDistinctListKeys(): List<String> =
        table
            .selectAll()
            .map { it[table.listKey] }
            .distinct()
            .sorted()

    @Transactional
    override fun deleteById(id: Long): Int = table.deleteWhere { table.id eq id }

    /** 按业务唯一键 (listKey, listType, keyType, keyValue) 回读插入行（V13 唯一约束保证至多一行） */
    private fun findLatest(
        listKey: String,
        listType: String,
        keyType: String,
        keyValue: String,
    ): NameListEntryRow? =
        table
            .selectAll()
            .where {
                (table.listKey eq listKey) and
                    (table.listType eq listType) and
                    (table.keyType eq keyType) and
                    (table.keyValue eq keyValue)
            }.singleOrNull()
            ?.let(::toRow)

    private fun toRow(row: ResultRow): NameListEntryRow =
        NameListEntryRow(
            id = row[table.id],
            listType = row[table.listType],
            keyType = row[table.keyType],
            keyValue = row[table.keyValue],
            listKey = row[table.listKey],
            reason = row[table.reason],
            source = row[table.sourceCol],
            expiredAt = row[table.expiredAt],
            createdBy = row[table.createdBy],
            createdAt = row[table.createdAt],
            updatedBy = row[table.updatedBy],
            updatedAt = row[table.updatedAt],
        )
}
