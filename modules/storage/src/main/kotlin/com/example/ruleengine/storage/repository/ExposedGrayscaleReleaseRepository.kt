package com.example.ruleengine.storage.repository

import com.example.ruleengine.domain.GrayscalePolicy
import com.example.ruleengine.domain.GrayscaleRelease
import com.example.ruleengine.domain.GrayscaleStatus
import com.example.ruleengine.domain.GrayscaleTarget
import com.example.ruleengine.domain.GrayscaleTargetType
import com.example.ruleengine.storage.StorageConflictException
import com.example.ruleengine.storage.StorageDataCorruptionException
import com.example.ruleengine.storage.grayscale.GrayscalePolicyCodec
import com.example.ruleengine.storage.table.GrayscaleConfigsTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/**
 * [GrayscaleReleaseRepository] 的 Exposed 实现。
 *
 * 新旧形态转换（详见 [GrayscalePolicyCodec]）：
 * - 领域 [GrayscalePolicy]（sealed，参数内聚）↔ 旧平铺列 strategy_type / grayscale_percentage /
 *   feature_rules / whitelist_ids
 * - 领域 [GrayscaleTarget]（type + key）↔ target_type / target_key；兼容列 rule_key 恒写 target.key
 *   （V14 之前灰度只作用于规则，target_key 即 rule_key，保持旧索引有效）
 * - 旧 grayscale_percentage"进度"语义不保留：COMPLETED 即全量，列值只承载 PERCENTAGE 策略参数
 */
internal class ExposedGrayscaleReleaseRepository : GrayscaleReleaseRepository {
    override fun save(release: GrayscaleRelease): GrayscaleRelease {
        val existingId = release.id
        return if (existingId == null) {
            insert(release)
        } else {
            update(existingId, release)
        }
    }

    private fun insert(release: GrayscaleRelease): GrayscaleRelease {
        val columns = GrayscalePolicyCodec.encode(release.policy)
        val inserted =
            GrayscaleConfigsTable.insert { statement ->
                statement[ruleKey] = release.target.key
                statement[currentVersion] = release.currentVersion
                statement[grayscaleVersion] = release.grayscaleVersion
                statement[grayscalePercentage] = columns.percentage
                statement[status] = release.status.name
                statement[startedAt] = release.startedAt
                statement[completedAt] = release.completedAt
                statement[targetType] = release.target.type.name
                statement[targetKey] = release.target.key
                statement[strategyType] = columns.strategyType
                statement[featureRules] = columns.featureRules
                statement[whitelistIds] = columns.whitelistIds
                statement[dualRunEnabled] = release.dualRunEnabled
                statement[description] = release.description
                statement[createdBy] = release.createdBy
                statement[createdAt] = release.createdAt
            }
        return release.copy(id = inserted[GrayscaleConfigsTable.id])
    }

    private fun update(
        id: Long,
        release: GrayscaleRelease,
    ): GrayscaleRelease {
        val columns = GrayscalePolicyCodec.encode(release.policy)
        val updatedRows =
            GrayscaleConfigsTable.update(where = { GrayscaleConfigsTable.id eq id }) { statement ->
                statement[ruleKey] = release.target.key
                statement[currentVersion] = release.currentVersion
                statement[grayscaleVersion] = release.grayscaleVersion
                statement[grayscalePercentage] = columns.percentage
                statement[status] = release.status.name
                statement[startedAt] = release.startedAt
                statement[completedAt] = release.completedAt
                statement[targetType] = release.target.type.name
                statement[targetKey] = release.target.key
                statement[strategyType] = columns.strategyType
                statement[featureRules] = columns.featureRules
                statement[whitelistIds] = columns.whitelistIds
                statement[dualRunEnabled] = release.dualRunEnabled
                statement[description] = release.description
                statement[createdBy] = release.createdBy
                statement[createdAt] = release.createdAt
            }
        require(updatedRows == 1) { throw StorageConflictException("GrayscaleRelease", "id=$id") }
        return release
    }

    override fun findById(id: Long): GrayscaleRelease? =
        GrayscaleConfigsTable
            .selectAll()
            .where { GrayscaleConfigsTable.id eq id }
            .singleOrNull()
            ?.let(::toRelease)

    override fun findRunningByTarget(target: GrayscaleTarget): GrayscaleRelease? =
        GrayscaleConfigsTable
            .selectAll()
            .where {
                (GrayscaleConfigsTable.targetType eq target.type.name) and
                    (GrayscaleConfigsTable.targetKey eq target.key) and
                    (GrayscaleConfigsTable.status eq GrayscaleStatus.RUNNING.name)
            }.orderBy(GrayscaleConfigsTable.id to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let(::toRelease)

    override fun findByTarget(target: GrayscaleTarget): List<GrayscaleRelease> =
        GrayscaleConfigsTable
            .selectAll()
            .where {
                (GrayscaleConfigsTable.targetType eq target.type.name) and
                    (GrayscaleConfigsTable.targetKey eq target.key)
            }.orderBy(GrayscaleConfigsTable.createdAt to SortOrder.DESC)
            .map(::toRelease)

    override fun findByStatus(status: GrayscaleStatus): List<GrayscaleRelease> =
        GrayscaleConfigsTable
            .selectAll()
            .where { GrayscaleConfigsTable.status eq status.name }
            .orderBy(GrayscaleConfigsTable.createdAt to SortOrder.DESC)
            .map(::toRelease)

    override fun findAll(): List<GrayscaleRelease> =
        GrayscaleConfigsTable
            .selectAll()
            .orderBy(GrayscaleConfigsTable.createdAt to SortOrder.DESC)
            .map(::toRelease)

    internal companion object {
        /** 行 → 领域模型；历史可空列按迁移默认值兜底，必填列缺失 fail fast */
        internal fun toRelease(row: ResultRow): GrayscaleRelease {
            val policy =
                GrayscalePolicyCodec.decode(
                    strategyType = row[GrayscaleConfigsTable.strategyType] ?: "PERCENTAGE",
                    percentage = row[GrayscaleConfigsTable.grayscalePercentage],
                    featureRules = row[GrayscaleConfigsTable.featureRules],
                    whitelistIds = row[GrayscaleConfigsTable.whitelistIds],
                )
            val status =
                decodeStatus(row[GrayscaleConfigsTable.status])
            val createdAt =
                row[GrayscaleConfigsTable.createdAt]
                    ?: throw StorageDataCorruptionException(
                        "grayscale_configs.created_at 为 NULL（id=${row[GrayscaleConfigsTable.id]}）",
                    )
            val createdBy =
                row[GrayscaleConfigsTable.createdBy]
                    ?: throw StorageDataCorruptionException(
                        "grayscale_configs.created_by 为 NULL（id=${row[GrayscaleConfigsTable.id]}）；领域模型要求创建人非空白",
                    )
            // target_key 为 NULL 的行是 V14 之前的历史数据（target 即 rule_key），按旧迁移语义回填
            val targetKey = row[GrayscaleConfigsTable.targetKey] ?: row[GrayscaleConfigsTable.ruleKey]
            return GrayscaleRelease(
                id = row[GrayscaleConfigsTable.id],
                target =
                    GrayscaleTarget(
                        type =
                            when (row[GrayscaleConfigsTable.targetType] ?: "RULE") {
                                GrayscaleTargetType.RULE.name -> {
                                    GrayscaleTargetType.RULE
                                }

                                GrayscaleTargetType.DECISION_FLOW.name -> {
                                    GrayscaleTargetType.DECISION_FLOW
                                }

                                else -> {
                                    throw StorageDataCorruptionException(
                                        "grayscale_configs.target_type 非法值: ${row[GrayscaleConfigsTable.targetType]}",
                                    )
                                }
                            },
                        key = targetKey,
                    ),
                currentVersion = row[GrayscaleConfigsTable.currentVersion],
                grayscaleVersion = row[GrayscaleConfigsTable.grayscaleVersion],
                policy = policy,
                status = status,
                dualRunEnabled = row[GrayscaleConfigsTable.dualRunEnabled] ?: false,
                description = row[GrayscaleConfigsTable.description],
                createdBy = createdBy,
                createdAt = createdAt,
                startedAt = row[GrayscaleConfigsTable.startedAt],
                completedAt = row[GrayscaleConfigsTable.completedAt],
            )
        }

        private fun decodeStatus(statusText: String?): GrayscaleStatus {
            val normalized = statusText ?: throw StorageDataCorruptionException("grayscale_configs.status 为 NULL")
            return try {
                GrayscaleStatus.valueOf(normalized)
            } catch (e: IllegalArgumentException) {
                throw StorageDataCorruptionException("grayscale_configs.status 非法值: $statusText", e)
            }
        }
    }
}
