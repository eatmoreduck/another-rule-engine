package com.example.ruleengine.decision.repo

import com.example.ruleengine.storage.repository.DecisionFlowMain
import com.example.ruleengine.storage.repository.DecisionFlowVersion
import com.example.ruleengine.storage.table.DecisionFlowVersionsTable
import com.example.ruleengine.storage.table.DecisionFlowsTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component

/**
 * 决策流只读读取器（decision-api 执行侧的最小读面）。
 *
 * 不复用 storage 的 [com.example.ruleengine.storage.repository.ExposedDecisionFlowRepository] 的原因：
 * 该实现标注 @Repository 且为 final class，会被 Spring 的注解驱动代理机制要求生成 CGLIB
 * 子类而失败（storage 库模块不经 kotlin-allopen，类不可继承）。执行侧只需两个只读方法，
 * 仿照 admin-api「部署物自持表对象直读既有表」的先例自持读面，列映射与迁移基线 V9+V14 一致。
 */
interface DecisionFlowReader {
    fun findMain(flowKey: String): DecisionFlowMain?

    fun findVersion(
        flowKey: String,
        version: Int,
    ): DecisionFlowVersion?
}

@Component
class ExposedDecisionFlowReader : DecisionFlowReader {
    override fun findMain(flowKey: String): DecisionFlowMain? =
        DecisionFlowsTable
            .selectAll()
            .where { DecisionFlowsTable.flowKey eq flowKey }
            .singleOrNull()
            ?.toMain()

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
