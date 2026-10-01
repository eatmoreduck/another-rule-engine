package com.eatmoreduck.ruleengine.storage.repository

import com.eatmoreduck.ruleengine.domain.RuleVersion
import com.eatmoreduck.ruleengine.domain.VersionStatus
import com.eatmoreduck.ruleengine.storage.EntityNotFoundException
import com.eatmoreduck.ruleengine.storage.StorageDataCorruptionException
import com.eatmoreduck.ruleengine.storage.table.RuleVersionsTable
import com.eatmoreduck.ruleengine.storage.table.RulesTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/**
 * [RuleVersionRepository] 的 Exposed 实现。
 *
 * rule_id 列（NOT NULL）由 ruleKey 现查 rules 表解析——领域模型以 ruleKey 逻辑关联，
 * 不携带数据库代理键；规则不存在时抛 [EntityNotFoundException]。
 * 定义载荷原样存取：领域 [RuleVersion.definitionJson] ↔ 历史列 groovy_script，仓储不解释内容。
 */
internal class ExposedRuleVersionRepository : RuleVersionRepository {
    override fun save(version: RuleVersion): RuleVersion {
        val ruleId =
            RulesTable
                .selectAll()
                .where { RulesTable.ruleKey eq version.ruleKey }
                .singleOrNull()
                ?.get(RulesTable.id)
                ?: throw EntityNotFoundException("Rule", "ruleKey=${version.ruleKey}")

        val existingRow =
            RuleVersionsTable
                .selectAll()
                .where { (RuleVersionsTable.ruleKey eq version.ruleKey) and (RuleVersionsTable.version eq version.version) }
                .singleOrNull()

        return if (existingRow == null) {
            insert(ruleId, version)
        } else {
            update(existingRow[RuleVersionsTable.id], ruleId, version)
        }
    }

    private fun insert(
        ruleId: Long,
        version: RuleVersion,
    ): RuleVersion {
        RuleVersionsTable.insert { statement ->
            statement[RuleVersionsTable.ruleId] = ruleId
            statement[ruleKey] = version.ruleKey
            statement[RuleVersionsTable.version] = version.version
            statement[groovyScript] = version.definitionJson
            statement[changeReason] = version.changeReason
            statement[changedBy] = version.changedBy
            statement[changedAt] = version.changedAt
            statement[isRollback] = version.isRollback
            statement[rollbackFromVersion] = version.rollbackFromVersion
            statement[status] = version.status.name
        }
        return version
    }

    private fun update(
        rowId: Long,
        ruleId: Long,
        version: RuleVersion,
    ): RuleVersion {
        val updatedRows =
            RuleVersionsTable.update(where = { RuleVersionsTable.id eq rowId }) { statement ->
                statement[RuleVersionsTable.ruleId] = ruleId
                statement[ruleKey] = version.ruleKey
                statement[RuleVersionsTable.version] = version.version
                statement[groovyScript] = version.definitionJson
                statement[changeReason] = version.changeReason
                statement[changedBy] = version.changedBy
                statement[changedAt] = version.changedAt
                statement[isRollback] = version.isRollback
                statement[rollbackFromVersion] = version.rollbackFromVersion
                statement[status] = version.status.name
            }
        require(updatedRows == 1) { throw EntityNotFoundException("RuleVersion", "id=$rowId") }
        return version
    }

    override fun findByRuleKeyAndVersion(
        ruleKey: String,
        version: Int,
    ): RuleVersion? =
        RuleVersionsTable
            .selectAll()
            .where { (RuleVersionsTable.ruleKey eq ruleKey) and (RuleVersionsTable.version eq version) }
            .singleOrNull()
            ?.let(::toRuleVersion)

    override fun existsByRuleKeyAndVersion(
        ruleKey: String,
        version: Int,
    ): Boolean =
        RuleVersionsTable
            .selectAll()
            .where { (RuleVersionsTable.ruleKey eq ruleKey) and (RuleVersionsTable.version eq version) }
            .any()

    override fun findByRuleKey(ruleKey: String): List<RuleVersion> =
        RuleVersionsTable
            .selectAll()
            .where { RuleVersionsTable.ruleKey eq ruleKey }
            .orderBy(RuleVersionsTable.version to SortOrder.DESC)
            .map(::toRuleVersion)

    override fun findCurrentVersion(ruleKey: String): RuleVersion? =
        RuleVersionsTable
            .selectAll()
            .where { RuleVersionsTable.ruleKey eq ruleKey }
            .orderBy(RuleVersionsTable.version to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let(::toRuleVersion)

    override fun findActiveVersion(ruleKey: String): RuleVersion? =
        RuleVersionsTable
            .selectAll()
            .where { (RuleVersionsTable.ruleKey eq ruleKey) and (RuleVersionsTable.status eq VersionStatus.ACTIVE.name) }
            .orderBy(RuleVersionsTable.version to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let(::toRuleVersion)

    override fun findByStatus(
        ruleKey: String,
        status: VersionStatus,
    ): List<RuleVersion> =
        RuleVersionsTable
            .selectAll()
            .where { (RuleVersionsTable.ruleKey eq ruleKey) and (RuleVersionsTable.status eq status.name) }
            .orderBy(RuleVersionsTable.version to SortOrder.DESC)
            .map(::toRuleVersion)

    internal companion object {
        /** 行 → 领域模型；历史行的 NULL 列按迁移默认值兜底（isRollback→false），status 缺失视为坏数据 */
        internal fun toRuleVersion(row: ResultRow): RuleVersion {
            val statusText =
                row[RuleVersionsTable.status]
                    ?: throw StorageDataCorruptionException(
                        "rule_versions.status 为 NULL（ruleKey=${row[RuleVersionsTable.ruleKey]}, version=${row[RuleVersionsTable.version]}）",
                    )
            val changedAt =
                row[RuleVersionsTable.changedAt]
                    ?: throw StorageDataCorruptionException(
                        "rule_versions.changed_at 为 NULL（ruleKey=${row[RuleVersionsTable.ruleKey]}, version=${row[RuleVersionsTable.version]}）",
                    )
            return RuleVersion(
                ruleKey = row[RuleVersionsTable.ruleKey],
                version = row[RuleVersionsTable.version],
                definitionJson = row[RuleVersionsTable.groovyScript],
                status =
                    try {
                        VersionStatus.valueOf(statusText)
                    } catch (e: IllegalArgumentException) {
                        throw StorageDataCorruptionException("rule_versions.status 非法值: $statusText", e)
                    },
                changeReason = row[RuleVersionsTable.changeReason],
                changedBy = row[RuleVersionsTable.changedBy],
                changedAt = changedAt,
                isRollback = row[RuleVersionsTable.isRollback] ?: false,
                rollbackFromVersion = row[RuleVersionsTable.rollbackFromVersion],
            )
        }
    }
}
