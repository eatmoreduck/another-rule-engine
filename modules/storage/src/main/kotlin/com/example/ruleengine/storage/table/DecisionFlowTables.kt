package com.example.ruleengine.storage.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

/**
 * 决策流表对象：映射 V9 建表 + V14 增列（active_version / 版本 status）。
 *
 * 决策流被两侧共享：decision-api（执行流程图）与 admin-api（流程管理 CRUD），
 * 仓储统一放在 storage 层作为单一事实源。其余约定同 [StorageTablesKt]。
 */
object DecisionFlowsTable : Table("decision_flows") {
    val id = long("id").autoIncrement()
    val flowKey = varchar("flow_key", 255).uniqueIndex()
    val flowName = varchar("flow_name", 255)
    val flowDescription = text("flow_description").nullable()
    val flowGraph = text("flow_graph")
    val version = integer("version")
    val status = varchar("status", 50)

    /** V14 引入：当前生效版本号（历史行由迁移回填为 version），NULL 视为未显式发布 */
    val activeVersion = integer("active_version").nullable()
    val createdBy = varchar("created_by", 255)
    val createdAt = timestamp("created_at")
    val updatedBy = varchar("updated_by", 255).nullable()
    val updatedAt = timestamp("updated_at").nullable()
    val enabled = bool("enabled")

    /** 旧 JPA @Version 遗留列（DEFAULT 0，可空）：新写入固定 0，读取忽略 */
    val optLockVersion = long("opt_lock_version").nullable()
    val environmentId = long("environment_id").nullable()

    override val primaryKey = PrimaryKey(id)
}

object DecisionFlowVersionsTable : Table("decision_flow_versions") {
    val id = long("id").autoIncrement()
    val flowId = long("flow_id")
    val flowKey = varchar("flow_key", 255)
    val version = integer("version")
    val flowGraph = text("flow_graph")
    val changeReason = text("change_reason").nullable()
    val changedBy = varchar("changed_by", 255)
    val changedAt = timestamp("changed_at")
    val isRollback = bool("is_rollback").nullable()
    val rollbackFromVersion = integer("rollback_from_version").nullable()

    /** V14 引入（DEFAULT 'ACTIVE'，历史行已回填 ARCHIVED 后再写入）：可空防御，映射层兜底 */
    val status = varchar("status", 20).nullable()

    override val primaryKey = PrimaryKey(id)
}
