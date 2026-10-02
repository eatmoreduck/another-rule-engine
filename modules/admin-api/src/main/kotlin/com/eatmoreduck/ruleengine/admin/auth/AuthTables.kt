package com.eatmoreduck.ruleengine.admin.auth

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

/**
 * 认证域表对象：sys_user / sys_role / sys_permission 及两张关联表。
 *
 * 说明：storage 模块的仓储只覆盖规则/版本/灰度/特征目录四个业务域（其聚合建模在
 * modules/domain），用户-角色-权限属于 admin-api 自身的认证职责，故本模块自持表对象
 * 直读既有表（列定义与初始化脚本 V1__init 的 sys_* 表严格一致），复用 storage 提供的
 * DataSource 与 SpringTransactionManager，不另建事务设施。
 */
object SysUsersTable : Table("sys_user") {
    val id = long("id").autoIncrement()
    val username = varchar("username", 100)
    val password = varchar("password", 255)
    val nickname = varchar("nickname", 100).nullable()
    val email = varchar("email", 200).nullable()
    val phone = varchar("phone", 20).nullable()
    val status = varchar("status", 20)
    val lastLoginAt = timestamp("last_login_at").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object SysRolesTable : Table("sys_role") {
    val id = long("id").autoIncrement()
    val roleCode = varchar("role_code", 100)
    val roleName = varchar("role_name", 100)

    /** V15 基线列（角色描述）：系统管理查询面（/system/roles、/system/users 的角色字段）消费 */
    val description = text("description").nullable()
    val status = varchar("status", 20)

    override val primaryKey = PrimaryKey(id)
}

object SysPermissionsTable : Table("sys_permission") {
    val id = long("id").autoIncrement()
    val permissionCode = varchar("permission_code", 200)
    val permissionName = varchar("permission_name", 200)

    /** V15 基线列（权限树元数据）：系统管理查询面（/system/permissions）消费 */
    val resourceType = varchar("resource_type", 50)
    val resourcePath = varchar("resource_path", 500).nullable()
    val method = varchar("method", 10).nullable()
    val parentId = long("parent_id").nullable()
    val sortOrder = integer("sort_order")
    val status = varchar("status", 20)

    override val primaryKey = PrimaryKey(id)
}

object SysUserRolesTable : Table("sys_user_role") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val roleId = long("role_id")

    override val primaryKey = PrimaryKey(id)
}

object SysRolePermissionsTable : Table("sys_role_permission") {
    val id = long("id").autoIncrement()
    val roleId = long("role_id")
    val permissionId = long("permission_id")

    override val primaryKey = PrimaryKey(id)
}
