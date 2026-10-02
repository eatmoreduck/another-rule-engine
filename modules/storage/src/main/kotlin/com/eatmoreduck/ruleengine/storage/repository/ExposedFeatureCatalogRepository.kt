package com.eatmoreduck.ruleengine.storage.repository

import com.eatmoreduck.ruleengine.storage.StorageConflictException
import com.eatmoreduck.ruleengine.storage.StorageDataCorruptionException
import com.eatmoreduck.ruleengine.storage.feature.FeatureAlias
import com.eatmoreduck.ruleengine.storage.feature.FeatureDefinition
import com.eatmoreduck.ruleengine.storage.table.FeatureAliasesTable
import com.eatmoreduck.ruleengine.storage.table.FeatureDefinitionsTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/**
 * [FeatureCatalogRepository] 的 Exposed 实现。
 *
 * 大小写不敏感语义沿用旧派生查询（findByCodeIgnoreCase 等）：经 lower() 归一后比较。
 * ACTIVE 状态过滤集中在 [resolveCode]（别名解析链路），目录管理查询不过滤状态。
 */
internal class ExposedFeatureCatalogRepository : FeatureCatalogRepository {
    override fun saveDefinition(definition: FeatureDefinition): FeatureDefinition {
        val existingId = definition.id
        return if (existingId == null) {
            insertDefinition(definition)
        } else {
            updateDefinition(existingId, definition)
        }
    }

    private fun insertDefinition(definition: FeatureDefinition): FeatureDefinition {
        val inserted =
            FeatureDefinitionsTable.insert { statement ->
                statement[code] = definition.code
                statement[name] = definition.name
                statement[dataType] = definition.dataType
                statement[sourceType] = definition.sourceType
                statement[exampleValue] = definition.exampleValue
                statement[description] = definition.description
                statement[status] = definition.status
                statement[owner] = definition.owner
                statement[createdAt] = definition.createdAt
                statement[updatedAt] = definition.updatedAt
            }
        return definition.copy(id = inserted[FeatureDefinitionsTable.id])
    }

    private fun updateDefinition(
        id: Long,
        definition: FeatureDefinition,
    ): FeatureDefinition {
        val updatedRows =
            FeatureDefinitionsTable.update(where = { FeatureDefinitionsTable.id eq id }) { statement ->
                statement[code] = definition.code
                statement[name] = definition.name
                statement[dataType] = definition.dataType
                statement[sourceType] = definition.sourceType
                statement[exampleValue] = definition.exampleValue
                statement[description] = definition.description
                statement[status] = definition.status
                statement[owner] = definition.owner
                statement[createdAt] = definition.createdAt
                statement[updatedAt] = definition.updatedAt
            }
        require(updatedRows == 1) { throw StorageConflictException("FeatureDefinition", "id=$id") }
        return definition
    }

    override fun findDefinitionByCode(code: String): FeatureDefinition? =
        FeatureDefinitionsTable
            .selectAll()
            .where { FeatureDefinitionsTable.code.lowerCase() eq code.lowercase() }
            .singleOrNull()
            ?.let(::toDefinition)

    override fun existsDefinitionWithCode(code: String): Boolean =
        FeatureDefinitionsTable
            .selectAll()
            .where { FeatureDefinitionsTable.code.lowerCase() eq code.lowercase() }
            .any()

    override fun findDefinitionsByCodes(codes: Collection<String>): List<FeatureDefinition> {
        if (codes.isEmpty()) return emptyList()
        val normalized = codes.map { it.lowercase() }
        return FeatureDefinitionsTable
            .selectAll()
            .where { FeatureDefinitionsTable.code.lowerCase() inList normalized }
            .map(::toDefinition)
            .sortedBy { normalized.indexOf(it.code.lowercase()) }
    }

    override fun searchDefinitions(query: FeatureDefinitionQuery): List<FeatureDefinition> {
        var select = FeatureDefinitionsTable.selectAll()
        query.keyword?.takeIf { it.isNotBlank() }?.let { keyword ->
            val pattern = "%${keyword.lowercase()}%"
            select =
                select.andWhere {
                    (FeatureDefinitionsTable.code.lowerCase() like pattern) or
                        (FeatureDefinitionsTable.name.lowerCase() like pattern)
                }
        }
        query.dataType?.let { select = select.andWhere { FeatureDefinitionsTable.dataType eq it } }
        query.sourceType?.let { select = select.andWhere { FeatureDefinitionsTable.sourceType eq it } }
        query.status?.let { select = select.andWhere { FeatureDefinitionsTable.status eq it } }
        return select
            .orderBy(FeatureDefinitionsTable.code to SortOrder.ASC)
            .limit(query.limit)
            .offset(query.offset)
            .map(::toDefinition)
    }

    override fun saveAlias(alias: FeatureAlias): FeatureAlias {
        FeatureAliasesTable.insert { statement ->
            statement[aliasCode] = alias.aliasCode
            statement[canonicalCode] = alias.canonicalCode
            statement[aliasType] = alias.aliasType
            statement[status] = alias.status
            statement[createdAt] = alias.createdAt
        }
        return alias
    }

    override fun findAliasByCode(aliasCode: String): FeatureAlias? =
        FeatureAliasesTable
            .selectAll()
            .where { FeatureAliasesTable.aliasCode.lowerCase() eq aliasCode.lowercase() }
            .singleOrNull()
            ?.let(::toAlias)

    override fun findAliasesByCanonicalCode(canonicalCode: String): List<FeatureAlias> =
        FeatureAliasesTable
            .selectAll()
            .where { FeatureAliasesTable.canonicalCode.lowerCase() eq canonicalCode.lowercase() }
            .orderBy(FeatureAliasesTable.aliasCode to SortOrder.ASC)
            .map(::toAlias)

    override fun deleteAliasesByCanonicalCode(canonicalCode: String): Int =
        FeatureAliasesTable.deleteWhere {
            FeatureAliasesTable.canonicalCode.lowerCase() eq canonicalCode.lowercase()
        }

    override fun resolveCode(code: String): FeatureDefinition? {
        findDefinitionByCode(code)?.let { direct ->
            // 直接命中的定义仅取 ACTIVE（旧 getByCode 语义：非 ACTIVE 视为不可用）
            return direct.takeIf { it.status == ACTIVE_STATUS }
        }
        val alias =
            findAliasByCode(code)
                ?.takeIf { it.status == ACTIVE_STATUS }
                ?: return null
        return findDefinitionByCode(alias.canonicalCode)?.takeIf { it.status == ACTIVE_STATUS }
    }

    internal companion object {
        private const val ACTIVE_STATUS = "ACTIVE"

        internal fun toDefinition(row: ResultRow): FeatureDefinition {
            val createdAt =
                row[FeatureDefinitionsTable.createdAt]
                    ?: throw StorageDataCorruptionException(
                        "feature_definition.created_at 为 NULL（code=${row[FeatureDefinitionsTable.code]}）",
                    )
            return FeatureDefinition(
                id = row[FeatureDefinitionsTable.id],
                code = row[FeatureDefinitionsTable.code],
                name = row[FeatureDefinitionsTable.name],
                dataType = row[FeatureDefinitionsTable.dataType],
                sourceType = row[FeatureDefinitionsTable.sourceType],
                exampleValue = row[FeatureDefinitionsTable.exampleValue],
                description = row[FeatureDefinitionsTable.description],
                status = row[FeatureDefinitionsTable.status],
                owner = row[FeatureDefinitionsTable.owner],
                createdAt = createdAt,
                // updated_at NOT NULL DEFAULT CURRENT_TIMESTAMP，与 created_at 同默认值
                updatedAt = row[FeatureDefinitionsTable.updatedAt],
            )
        }

        internal fun toAlias(row: ResultRow): FeatureAlias =
            FeatureAlias(
                id = row[FeatureAliasesTable.id],
                aliasCode = row[FeatureAliasesTable.aliasCode],
                canonicalCode = row[FeatureAliasesTable.canonicalCode],
                aliasType = row[FeatureAliasesTable.aliasType],
                status = row[FeatureAliasesTable.status],
                createdAt =
                    row[FeatureAliasesTable.createdAt]
                        ?: throw StorageDataCorruptionException(
                            "feature_alias.created_at 为 NULL（aliasCode=${row[FeatureAliasesTable.aliasCode]}）",
                        ),
            )
    }
}
