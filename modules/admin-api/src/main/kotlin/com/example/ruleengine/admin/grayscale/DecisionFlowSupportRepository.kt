package com.example.ruleengine.admin.grayscale

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

/** 决策流主表行（灰度校验、全量切换与引用扫描所需的最小列集） */
data class DecisionFlowMain(
    val id: Long?,
    val flowKey: String,
    val flowName: String,
    val flowGraph: String,
    /** 最新内容版本号（decision_flows.version） */
    val version: Int,
    /** 当前生效版本号（V14 引入的 active_version，可空） */
    val activeVersion: Int?,
)

/** 决策流版本行（灰度版本存在性校验与 CANARY 推进所需的最小列集） */
data class DecisionFlowVersionRow(
    val flowKey: String,
    val version: Int,
    val flowGraph: String,
    /** V14 引入的版本状态列（历史行可能为 NULL，按迁移默认值兜底为 ACTIVE） */
    val status: String?,
)

/**
 * 决策流只读/切换支撑（供灰度发布的 DECISION_FLOW 目标与规则引用扫描使用）。
 *
 * 说明：决策流管理属于阶段 3 范围，storage 模块未建模 decision_flows /
 * decision_flow_versions 的仓储；但旧灰度契约完整覆盖 DECISION_FLOW 目标
 * （创建校验、启动、全量切换），故本模块自持最小表对象直读既有表
 * （列定义与迁移基线 V9/V14 一致），维持旧灰度 API 的行为面。
 */
interface DecisionFlowSupportRepository {
    fun findFlowMain(flowKey: String): DecisionFlowMain?

    fun findAllFlowMains(): List<DecisionFlowMain>

    fun findFlowVersion(
        flowKey: String,
        version: Int,
    ): DecisionFlowVersionRow?

    /** 全量切换：主表版本指针与流程图推进到灰度版本（对应旧 completeDecisionFlowGrayscale） */
    fun switchFlowVersion(
        flowKey: String,
        grayscaleVersion: Int,
        flowGraph: String,
    )

    /** 灰度启动时把决策流灰度版本推进为 CANARY（阶段 1 修正：CANARY 状态真正写入） */
    fun markFlowVersionCanary(
        flowKey: String,
        version: Int,
    )
}

/** [DecisionFlowSupportRepository] 的 Exposed 实现 */
@Repository
class ExposedDecisionFlowSupportRepository : DecisionFlowSupportRepository {
    override fun findFlowMain(flowKey: String): DecisionFlowMain? =
        DecisionFlowsTable
            .selectAll()
            .where { DecisionFlowsTable.flowKey eq flowKey }
            .singleOrNull()
            ?.let(::toMain)

    override fun findAllFlowMains(): List<DecisionFlowMain> = DecisionFlowsTable.selectAll().map(::toMain)

    override fun findFlowVersion(
        flowKey: String,
        version: Int,
    ): DecisionFlowVersionRow? =
        DecisionFlowVersionsTable
            .selectAll()
            .where {
                (DecisionFlowVersionsTable.flowKey eq flowKey) and (DecisionFlowVersionsTable.version eq version)
            }.singleOrNull()
            ?.let(::toVersionRow)

    override fun switchFlowVersion(
        flowKey: String,
        grayscaleVersion: Int,
        flowGraph: String,
    ) {
        DecisionFlowsTable.update({ DecisionFlowsTable.flowKey eq flowKey }) { statement ->
            statement[version] = grayscaleVersion
            statement[DecisionFlowsTable.flowGraph] = flowGraph
            statement[activeVersion] = grayscaleVersion
        }
    }

    override fun markFlowVersionCanary(
        flowKey: String,
        version: Int,
    ) {
        DecisionFlowVersionsTable.update(
            where = {
                (DecisionFlowVersionsTable.flowKey eq flowKey) and (DecisionFlowVersionsTable.version eq version)
            },
        ) { statement ->
            statement[status] = "CANARY"
        }
    }

    private fun toMain(row: ResultRow): DecisionFlowMain =
        DecisionFlowMain(
            id = row[DecisionFlowsTable.id],
            flowKey = row[DecisionFlowsTable.flowKey],
            flowName = row[DecisionFlowsTable.flowName],
            flowGraph = row[DecisionFlowsTable.flowGraph],
            version = row[DecisionFlowsTable.version],
            activeVersion = row[DecisionFlowsTable.activeVersion],
        )

    private fun toVersionRow(row: ResultRow): DecisionFlowVersionRow =
        DecisionFlowVersionRow(
            flowKey = row[DecisionFlowVersionsTable.flowKey],
            version = row[DecisionFlowVersionsTable.version],
            flowGraph = row[DecisionFlowVersionsTable.flowGraph],
            status = row[DecisionFlowVersionsTable.status],
        )

    /** decision_flows 最小表对象（列与 V9/V14 基线一致，未列出的列不读写） */
    private object DecisionFlowsTable : Table("decision_flows") {
        val id = long("id").autoIncrement()
        val flowKey = varchar("flow_key", 255)
        val flowName = varchar("flow_name", 255)
        val flowGraph = text("flow_graph")
        val version = integer("version")
        val activeVersion = integer("active_version").nullable()
    }

    /** decision_flow_versions 最小表对象 */
    private object DecisionFlowVersionsTable : Table("decision_flow_versions") {
        val flowKey = varchar("flow_key", 255)
        val version = integer("version")
        val flowGraph = text("flow_graph")
        val status = varchar("status", 20).nullable()
    }
}
