package com.example.ruleengine.admin.auth

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 认证域用户行模型（不含密码之外的业务信息） */
data class AuthUser(
    val id: Long,
    val username: String,
    val password: String,
    val nickname: String?,
    val email: String?,
    val phone: String?,
    val status: String,
)

/**
 * 认证数据访问：用户查询、角色/权限码解析、最近登录时间回写。
 *
 * 查询语义照搬旧 SysPermissionRepository 的两个 JPQL：
 * - 角色码：user → user_role → role（status=ACTIVE），DISTINCT；
 * - 权限码：user → user_role → role_permission → permission（仅过滤 permission.status=ACTIVE，
 *   注意旧查询不校验角色状态，此处保持一致）。
 */
interface AuthRepository {
    fun findByUsername(username: String): AuthUser?

    fun findById(userId: Long): AuthUser?

    /** 回写最近登录时间（旧 AuthService 登录成功后的动作） */
    fun updateLastLoginAt(
        userId: Long,
        at: Instant,
    )

    /** 用户角色码列表（仅 ACTIVE 角色） */
    fun findRoleCodes(userId: Long): List<String>

    /** 用户权限码列表（仅 ACTIVE 权限） */
    fun findPermissionCodes(userId: Long): List<String>
}

/**
 * [AuthRepository] 的 Exposed 实现：直读 sys_* 五张表（列定义见 [AuthTables]）。
 *
 * 类级只读事务：认证路径存在"事务外"的调用方（如 Sa-Token 注解校验回调 StpInterface），
 * 仓储自带事务边界保证任何入口都有 Exposed 事务上下文；外层已有事务时按 REQUIRED 合并。
 */
@Repository
@Transactional(readOnly = true)
class ExposedAuthRepository : AuthRepository {
    private val sysUsers = SysUsersTable
    private val sysRoles = SysRolesTable
    private val sysPermissions = SysPermissionsTable
    private val sysUserRoles = SysUserRolesTable
    private val sysRolePermissions = SysRolePermissionsTable

    override fun findByUsername(username: String): AuthUser? =
        sysUsers
            .selectAll()
            .where { sysUsers.username eq username }
            .singleOrNull()
            ?.let(::toUser)

    override fun findById(userId: Long): AuthUser? =
        sysUsers
            .selectAll()
            .where { sysUsers.id eq userId }
            .singleOrNull()
            ?.let(::toUser)

    override fun updateLastLoginAt(
        userId: Long,
        at: Instant,
    ) {
        sysUsers.update({ sysUsers.id eq userId }) {
            it[sysUsers.lastLoginAt] = at
            it[sysUsers.updatedAt] = at
        }
    }

    override fun findRoleCodes(userId: Long): List<String> =
        sysRoles
            .join(sysUserRoles, JoinType.INNER, additionalConstraint = { sysUserRoles.roleId eq sysRoles.id })
            .selectAll()
            .where { (sysUserRoles.userId eq userId) and (sysRoles.status eq "ACTIVE") }
            .map { it[sysRoles.roleCode] }
            .distinct()

    override fun findPermissionCodes(userId: Long): List<String> =
        sysPermissions
            .join(
                sysRolePermissions,
                JoinType.INNER,
                additionalConstraint = { sysRolePermissions.permissionId eq sysPermissions.id },
            ).join(
                sysUserRoles,
                JoinType.INNER,
                additionalConstraint = { sysUserRoles.roleId eq sysRolePermissions.roleId },
            ).selectAll()
            .where { (sysUserRoles.userId eq userId) and (sysPermissions.status eq "ACTIVE") }
            .map { it[sysPermissions.permissionCode] }

    private fun toUser(row: ResultRow): AuthUser =
        AuthUser(
            id = row[sysUsers.id],
            username = row[sysUsers.username],
            password = row[sysUsers.password],
            nickname = row[sysUsers.nickname],
            email = row[sysUsers.email],
            phone = row[sysUsers.phone],
            status = row[sysUsers.status],
        )
}
