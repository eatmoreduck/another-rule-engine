package com.eatmoreduck.ruleengine.storage.repository

import com.eatmoreduck.ruleengine.domain.Rule
import com.eatmoreduck.ruleengine.domain.RuleStatus
import com.eatmoreduck.ruleengine.storage.EntityNotFoundException
import com.eatmoreduck.ruleengine.storage.StorageDataCorruptionException
import com.eatmoreduck.ruleengine.storage.table.RulesTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/**
 * [RuleRepository] 的 Exposed 实现。
 *
 * 主表遗留列的写语义：
 * - groovy_script：领域定义载荷已收敛到 rule_versions，此处恒写占位空串（NOT NULL 约束仍在）
 * - opt_lock_version：旧 JPA @Version 遗留，领域模型已剔除乐观锁，恒写 0
 * - enabled/deleted 两列承载 [RuleStatus] 三态（见 [toStatus]/[toColumns]）
 */
internal class ExposedRuleRepository : RuleRepository {
    override fun save(rule: Rule): Rule {
        val existingId = rule.id
        return if (existingId == null) {
            insert(rule)
        } else {
            update(existingId, rule)
        }
    }

    private fun insert(rule: Rule): Rule {
        val (enabled, deleted) = toColumns(rule.status)
        val inserted =
            RulesTable.insert { statement ->
                statement[ruleKey] = rule.ruleKey
                statement[ruleName] = rule.ruleName
                statement[ruleDescription] = rule.ruleDescription
                statement[groovyScript] = LEGACY_SCRIPT_PLACEHOLDER
                statement[version] = rule.currentVersion
                statement[activeVersion] = rule.activeVersion
                statement[RulesTable.enabled] = enabled
                statement[RulesTable.deleted] = deleted
                statement[createdBy] = rule.createdBy
                statement[createdAt] = rule.createdAt
                statement[updatedBy] = rule.updatedBy
                statement[updatedAt] = rule.updatedAt
                statement[optLockVersion] = 0L
                statement[environmentId] = rule.environmentId
                statement[teamId] = rule.teamId
            }
        return rule.copy(id = inserted[RulesTable.id])
    }

    private fun update(
        id: Long,
        rule: Rule,
    ): Rule {
        val (enabled, deleted) = toColumns(rule.status)
        val updatedRows =
            RulesTable.update(where = { RulesTable.id eq id }) { statement ->
                statement[ruleKey] = rule.ruleKey
                statement[ruleName] = rule.ruleName
                statement[ruleDescription] = rule.ruleDescription
                statement[groovyScript] = LEGACY_SCRIPT_PLACEHOLDER
                statement[version] = rule.currentVersion
                statement[activeVersion] = rule.activeVersion
                statement[RulesTable.enabled] = enabled
                statement[RulesTable.deleted] = deleted
                statement[createdBy] = rule.createdBy
                statement[createdAt] = rule.createdAt
                statement[updatedBy] = rule.updatedBy
                statement[updatedAt] = rule.updatedAt
                statement[optLockVersion] = 0L
                statement[environmentId] = rule.environmentId
                statement[teamId] = rule.teamId
            }
        require(updatedRows == 1) { throw EntityNotFoundException("Rule", "id=$id") }
        return rule
    }

    override fun findByRuleKey(ruleKey: String): Rule? =
        RulesTable
            .selectAll()
            .where { RulesTable.ruleKey eq ruleKey }
            .singleOrNull()
            ?.let(::toRule)

    override fun existsByRuleKey(ruleKey: String): Boolean =
        RulesTable
            .selectAll()
            .where { RulesTable.ruleKey eq ruleKey }
            .any()

    override fun findEnabled(environmentId: Long?): List<Rule> {
        val base =
            RulesTable
                .selectAll()
                .where { RulesTable.deleted eq false }
        val filtered =
            if (environmentId == null) {
                base
            } else {
                base.andWhere { RulesTable.environmentId eq environmentId }
            }
        return filtered.orderBy(RulesTable.id to SortOrder.ASC).map(::toRule)
    }

    override fun search(query: RuleSearchQuery): List<Rule> {
        val conditions =
            buildList {
                add(RulesTable.deleted eq query.includeDeleted)
                query.keyword?.takeIf { it.isNotBlank() }?.let { keyword ->
                    val pattern = "%${keyword.lowercase()}%"
                    add(
                        (RulesTable.ruleKey.lowerCase() like pattern) or
                            (RulesTable.ruleName.lowerCase() like pattern),
                    )
                }
                query.teamId?.let { add(RulesTable.teamId eq it) }
                query.environmentId?.let { add(RulesTable.environmentId eq it) }
            }
        return RulesTable
            .selectAll()
            .where { conditions.reduce { left, right -> left and right } }
            .orderBy(RulesTable.id to SortOrder.ASC)
            .limit(query.limit)
            .offset(query.offset)
            .map(::toRule)
    }

    internal companion object {
        /** 已废弃的主表脚本列占位（NOT NULL 约束仍在，见 [RulesTable.groovyScript] 注释） */
        internal const val LEGACY_SCRIPT_PLACEHOLDER = ""

        /**
         * 领域状态 → (enabled, deleted) 列对。
         * DELETED 时 enabled 恒写 false：确定性写入，回读以 deleted 列优先，转换无损。
         */
        internal fun toColumns(status: RuleStatus): Pair<Boolean, Boolean> =
            when (status) {
                RuleStatus.ENABLED -> true to false
                RuleStatus.DISABLED -> false to false
                RuleStatus.DELETED -> false to true
            }

        /** (enabled, deleted) 列对 → 领域状态（deleted 优先，与旧软删除语义一致） */
        internal fun toStatus(
            enabled: Boolean,
            deleted: Boolean,
        ): RuleStatus =
            when {
                deleted -> RuleStatus.DELETED
                enabled -> RuleStatus.ENABLED
                else -> RuleStatus.DISABLED
            }

        /** 行 → 领域模型；NULL 且无兜底默认的必填列视为坏数据 fail fast */
        internal fun toRule(row: ResultRow): Rule {
            val enabled = row[RulesTable.enabled]
            val deleted = row[RulesTable.deleted]
            val currentVersion = row[RulesTable.version]
            val createdAt =
                row[RulesTable.createdAt]
                    ?: throw StorageDataCorruptionException("rules.created_at 为 NULL（ruleKey=${row[RulesTable.ruleKey]}）")
            return Rule(
                id = row[RulesTable.id],
                ruleKey = row[RulesTable.ruleKey],
                ruleName = row[RulesTable.ruleName],
                ruleDescription = row[RulesTable.ruleDescription],
                status = toStatus(enabled, deleted),
                currentVersion = currentVersion,
                activeVersion = row[RulesTable.activeVersion],
                environmentId = row[RulesTable.environmentId],
                teamId = row[RulesTable.teamId],
                createdBy = row[RulesTable.createdBy],
                createdAt = createdAt,
                updatedBy = row[RulesTable.updatedBy],
                updatedAt = row[RulesTable.updatedAt],
            )
        }
    }
}
