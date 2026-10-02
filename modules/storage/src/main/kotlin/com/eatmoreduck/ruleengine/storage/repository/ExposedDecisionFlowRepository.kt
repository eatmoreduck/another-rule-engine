package com.eatmoreduck.ruleengine.storage.repository

import com.eatmoreduck.ruleengine.storage.table.DecisionFlowVersionsTable
import com.eatmoreduck.ruleengine.storage.table.DecisionFlowsTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class ExposedDecisionFlowRepository : DecisionFlowRepository {
    override fun findMain(flowKey: String): DecisionFlowMain? =
        DecisionFlowsTable
            .selectAll()
            .where { DecisionFlowsTable.flowKey eq flowKey }
            .singleOrNull()
            ?.toMain()

    override fun findAllMains(): List<DecisionFlowMain> =
        DecisionFlowsTable
            .selectAll()
            .where { not(DecisionFlowsTable.status eq "DELETED") }
            .map { it.toMain() }

    override fun findVersion(
        flowKey: String,
        version: Int,
    ): DecisionFlowVersion? =
        DecisionFlowVersionsTable
            .selectAll()
            .where {
                (DecisionFlowVersionsTable.flowKey eq flowKey) and (DecisionFlowVersionsTable.version eq version)
            }.singleOrNull()
            ?.toVersion()

    override fun findVersionsByFlowKey(flowKey: String): List<DecisionFlowVersion> =
        DecisionFlowVersionsTable
            .selectAll()
            .where { DecisionFlowVersionsTable.flowKey eq flowKey }
            .orderBy(DecisionFlowVersionsTable.version to SortOrder.DESC)
            .map { it.toVersion() }

    override fun insertMain(main: DecisionFlowMain): DecisionFlowMain {
        DecisionFlowsTable.insert { statement ->
            statement[DecisionFlowsTable.flowKey] = main.flowKey
            statement[DecisionFlowsTable.flowName] = main.flowName
            statement[DecisionFlowsTable.flowDescription] = main.flowDescription
            statement[DecisionFlowsTable.flowGraph] = main.flowGraph
            statement[DecisionFlowsTable.version] = main.version
            statement[DecisionFlowsTable.activeVersion] = main.activeVersion
            statement[DecisionFlowsTable.status] = main.status
            statement[DecisionFlowsTable.createdBy] = main.createdBy
            statement[DecisionFlowsTable.createdAt] = main.createdAt
            statement[DecisionFlowsTable.updatedBy] = main.updatedBy
            statement[DecisionFlowsTable.updatedAt] = main.updatedAt
            statement[DecisionFlowsTable.enabled] = main.enabled
            statement[DecisionFlowsTable.environmentId] = main.environmentId
        }
        return findMain(main.flowKey)
            ?: throw IllegalStateException("决策流插入后回读失败: ${main.flowKey}")
    }

    override fun insertVersion(version: DecisionFlowVersion): DecisionFlowVersion {
        DecisionFlowVersionsTable.insert { statement ->
            statement[DecisionFlowVersionsTable.flowId] = version.flowId
            statement[DecisionFlowVersionsTable.flowKey] = version.flowKey
            statement[DecisionFlowVersionsTable.version] = version.version
            statement[DecisionFlowVersionsTable.flowGraph] = version.flowGraph
            statement[DecisionFlowVersionsTable.changeReason] = version.changeReason
            statement[DecisionFlowVersionsTable.changedBy] = version.changedBy
            statement[DecisionFlowVersionsTable.changedAt] = version.changedAt
            statement[DecisionFlowVersionsTable.isRollback] = version.isRollback
            statement[DecisionFlowVersionsTable.rollbackFromVersion] = version.rollbackFromVersion
            statement[DecisionFlowVersionsTable.status] = version.status
        }
        return findVersion(version.flowKey, version.version)
            ?: throw IllegalStateException("决策流版本插入后回读失败: ${version.flowKey}v${version.version}")
    }

    override fun updateMainMeta(
        flowKey: String,
        flowName: String,
        flowDescription: String?,
        environmentId: Long?,
        updatedBy: String,
    ): Int =
        DecisionFlowsTable.update({ DecisionFlowsTable.flowKey eq flowKey }) { statement ->
            statement[DecisionFlowsTable.flowName] = flowName
            statement[DecisionFlowsTable.flowDescription] = flowDescription
            statement[DecisionFlowsTable.environmentId] = environmentId
            statement[DecisionFlowsTable.updatedBy] = updatedBy
            statement[DecisionFlowsTable.updatedAt] = Instant.now()
        }

    override fun updateMainPointer(
        flowKey: String,
        version: Int,
        activeVersion: Int?,
        flowGraph: String,
        updatedBy: String,
    ): Int =
        DecisionFlowsTable.update({ DecisionFlowsTable.flowKey eq flowKey }) { statement ->
            statement[DecisionFlowsTable.version] = version
            statement[DecisionFlowsTable.activeVersion] = activeVersion
            statement[DecisionFlowsTable.flowGraph] = flowGraph
            statement[DecisionFlowsTable.updatedBy] = updatedBy
            statement[DecisionFlowsTable.updatedAt] = Instant.now()
        }

    override fun updateMainStatus(
        flowKey: String,
        status: String,
        updatedBy: String,
    ): Int =
        DecisionFlowsTable.update({ DecisionFlowsTable.flowKey eq flowKey }) { statement ->
            statement[DecisionFlowsTable.status] = status
            statement[DecisionFlowsTable.updatedBy] = updatedBy
            statement[DecisionFlowsTable.updatedAt] = Instant.now()
        }

    override fun setMainEnabled(
        flowKey: String,
        enabled: Boolean,
        updatedBy: String,
    ): Int =
        DecisionFlowsTable.update({ DecisionFlowsTable.flowKey eq flowKey }) { statement ->
            statement[DecisionFlowsTable.enabled] = enabled
            statement[DecisionFlowsTable.updatedBy] = updatedBy
            statement[DecisionFlowsTable.updatedAt] = Instant.now()
        }

    override fun updateVersionStatus(
        flowKey: String,
        version: Int,
        status: String,
    ): Int =
        DecisionFlowVersionsTable.update({
            (DecisionFlowVersionsTable.flowKey eq flowKey) and (DecisionFlowVersionsTable.version eq version)
        }) { statement ->
            statement[DecisionFlowVersionsTable.status] = status
        }

    private fun ResultRow.toMain(): DecisionFlowMain =
        DecisionFlowMain(
            id = this[DecisionFlowsTable.id],
            flowKey = this[DecisionFlowsTable.flowKey],
            flowName = this[DecisionFlowsTable.flowName],
            flowDescription = this[DecisionFlowsTable.flowDescription],
            flowGraph = this[DecisionFlowsTable.flowGraph],
            version = this[DecisionFlowsTable.version],
            activeVersion = this[DecisionFlowsTable.activeVersion],
            status = this[DecisionFlowsTable.status],
            createdBy = this[DecisionFlowsTable.createdBy],
            createdAt = this[DecisionFlowsTable.createdAt],
            updatedBy = this[DecisionFlowsTable.updatedBy],
            updatedAt = this[DecisionFlowsTable.updatedAt],
            enabled = this[DecisionFlowsTable.enabled],
            environmentId = this[DecisionFlowsTable.environmentId],
        )

    private fun ResultRow.toVersion(): DecisionFlowVersion =
        DecisionFlowVersion(
            id = this[DecisionFlowVersionsTable.id],
            flowId = this[DecisionFlowVersionsTable.flowId],
            flowKey = this[DecisionFlowVersionsTable.flowKey],
            version = this[DecisionFlowVersionsTable.version],
            flowGraph = this[DecisionFlowVersionsTable.flowGraph],
            changeReason = this[DecisionFlowVersionsTable.changeReason],
            changedBy = this[DecisionFlowVersionsTable.changedBy],
            changedAt = this[DecisionFlowVersionsTable.changedAt],
            isRollback = this[DecisionFlowVersionsTable.isRollback] ?: false,
            rollbackFromVersion = this[DecisionFlowVersionsTable.rollbackFromVersion],
            status = this[DecisionFlowVersionsTable.status] ?: "ACTIVE",
        )
}
