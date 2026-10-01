package com.eatmoreduck.ruleengine.decision.auth

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository

/**
 * 认证域最小只读表对象：决策侧仅消费"用户 → 角色/权限码"两条链路，
 * 不承担用户管理（登录/改密属 admin-api 职责），故自持窄列集直读既有表
 * （列定义与迁移基线 V15/V16/V17 严格一致），复用 storage 的 DataSource 与
 * SpringTransactionManager。与 admin-api 的 AuthTables.kt 决策口径一致。
 */
internal object SysRolesTable : Table("sys_role") {
    val id = long("id")
    val roleCode = varchar("role_code", 100)
    val status = varchar("status", 20)

    override val primaryKey = PrimaryKey(id)
}

internal object SysPermissionsTable : Table("sys_permission") {
    val id = long("id")
    val permissionCode = varchar("permission_code", 200)
    val status = varchar("status", 20)

    override val primaryKey = PrimaryKey(id)
}

internal object SysUserRolesTable : Table("sys_user_role") {
    val id = long("id")
    val userId = long("user_id")
    val roleId = long("role_id")

    override val primaryKey = PrimaryKey(id)
}

internal object SysRolePermissionsTable : Table("sys_role_permission") {
    val id = long("id")
    val roleId = long("role_id")
    val permissionId = long("permission_id")

    override val primaryKey = PrimaryKey(id)
}

/**
 * 决策侧认证查询（只读）：@SaCheckPermission 注解校验的角色/权限数据源。
 *
 * 查询语义与 admin-api 的 AuthRepository 一致（照搬旧 SysPermissionRepository 的 JPQL）：
 * - 角色码：user → user_role → role（status=ACTIVE），DISTINCT；
 * - 权限码：user → user_role → role_permission → permission（仅过滤 permission.status=ACTIVE，
 *   与旧查询一致不校验角色状态）。
 */
@Repository
class DecisionAuthRepository {
    fun findRoleCodes(userId: Long): List<String> =
        transaction {
            SysRolesTable
                .join(SysUserRolesTable, JoinType.INNER, additionalConstraint = { SysUserRolesTable.roleId eq SysRolesTable.id })
                .selectAll()
                .where { (SysUserRolesTable.userId eq userId) and (SysRolesTable.status eq "ACTIVE") }
                .map { it[SysRolesTable.roleCode] }
                .distinct()
        }

    fun findPermissionCodes(userId: Long): List<String> =
        transaction {
            SysPermissionsTable
                .join(
                    SysRolePermissionsTable,
                    JoinType.INNER,
                    additionalConstraint = { SysRolePermissionsTable.permissionId eq SysPermissionsTable.id },
                ).join(
                    SysUserRolesTable,
                    JoinType.INNER,
                    additionalConstraint = { SysUserRolesTable.roleId eq SysRolePermissionsTable.roleId },
                ).selectAll()
                .where { (SysUserRolesTable.userId eq userId) and (SysPermissionsTable.status eq "ACTIVE") }
                .map { it[SysPermissionsTable.permissionCode] }
        }
}
