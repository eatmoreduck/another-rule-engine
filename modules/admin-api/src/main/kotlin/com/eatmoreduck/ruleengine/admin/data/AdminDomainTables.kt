package com.eatmoreduck.ruleengine.admin.data

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

/**
 * 名单 / 审计 / 环境三张表的表对象。
 *
 * 说明：storage 模块的仓储只覆盖规则/版本/灰度/特征目录/决策流五个业务域，
 * 黑白名单、审计日志、环境管理属于 admin-api 的管理职责（阶段 2c 范围），
 * 故本模块自持表对象直读既有表（列定义与迁移基线 V3/V7/V11/V12/V13 严格一致，
 * 风格照 2b 的 AuthTables 先例），复用 storage 提供的 DataSource 与
 * SpringTransactionManager，不另建事务设施，也不新增迁移脚本。
 */
object NameListTable : Table("name_list") {
    val id = long("id").autoIncrement()
    val listType = varchar("list_type", 10)
    val keyType = varchar("key_type", 20)
    val keyValue = varchar("key_value", 256)
    val listKey = varchar("list_key", 255)
    val reason = text("reason").nullable()

    /** 列名 source 与 Exposed ColumnSet.source 成员重名，属性名改用 sourceCol 避让 */
    val sourceCol = varchar("source", 255).nullable()
    val expiredAt = timestamp("expired_at").nullable()
    val createdBy = varchar("created_by", 255)
    val createdAt = timestamp("created_at")
    val updatedBy = varchar("updated_by", 255).nullable()
    val updatedAt = timestamp("updated_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object AuditLogsTable : Table("audit_logs") {
    val id = long("id").autoIncrement()
    val entityType = varchar("entity_type", 100)
    val entityId = varchar("entity_id", 255)
    val operation = varchar("operation", 50)
    val operationDetail = text("operation_detail").nullable()
    val operator = varchar("operator", 255)
    val operatorIp = varchar("operator_ip", 50).nullable()

    /** DDL 仅 DEFAULT 无 NOT NULL：可空以防御异常行，映射层兜底为写入时刻 */
    val operationTime = timestamp("operation_time").nullable()
    val status = varchar("status", 50)
    val errorMessage = text("error_message").nullable()
    val requestId = varchar("request_id", 100).nullable()

    override val primaryKey = PrimaryKey(id)
}

object EnvironmentsTable : Table("environments") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 100)
    val type = varchar("type", 20)
    val description = text("description").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
