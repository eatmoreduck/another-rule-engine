package com.eatmoreduck.ruleengine.admin.system

import com.eatmoreduck.ruleengine.admin.auth.SysPermissionsTable
import com.eatmoreduck.ruleengine.admin.auth.SysRolePermissionsTable
import com.eatmoreduck.ruleengine.admin.auth.SysRolesTable
import com.eatmoreduck.ruleengine.admin.auth.SysUserRolesTable
import com.eatmoreduck.ruleengine.admin.auth.SysUsersTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 系统管理用户行（含创建时间；不含密码，查询面不暴露凭据） */
data class SystemUserRow(
    val id: Long,
    val username: String,
    val nickname: String?,
    val email: String?,
    val phone: String?,
    val status: String,
    val createdAt: Instant?,
)

/** 系统管理角色行 */
data class SystemRoleRow(
    val id: Long,
    val roleCode: String,
    val roleName: String,
    val description: String?,
    val status: String,
)

/** 系统管理权限行 */
data class SystemPermissionRow(
    val id: Long,
    val permissionCode: String,
    val permissionName: String,
    val resourceType: String,
    val resourcePath: String?,
    val method: String?,
    val parentId: Long?,
    val sortOrder: Int,
)

/** 用户-角色关联行 */
data class SystemUserRoleRow(
    val userId: Long,
    val roleId: Long,
)

/**
 * 系统管理查询面数据访问（对应旧 UserManagementService/RoleManagementService 的
 * listUsers/listRoles/listPermissions 读取职责，sys_* 五张表）。
 *
 * 查询语义照搬旧 JPA 派生查询：不过滤状态（含 DISABLED 行），关联顺序按关联表主键。
 */
interface SystemQueryRepository {
    /** 全部用户（id 升序；旧 findAll） */
    fun findAllUsers(): List<SystemUserRow>

    /** 全部角色（id 升序；旧 findAll） */
    fun findAllRoles(): List<SystemRoleRow>

    /** 全部权限（id 升序；旧 findAll） */
    fun findAllPermissions(): List<SystemPermissionRow>

    /** 全部用户-角色关联（id 升序） */
    fun findAllUserRoles(): List<SystemUserRoleRow>

    /** 角色 → 权限码列表（sys_role_permission → sys_permission，按关联表 id 升序） */
    fun findPermissionCodesByRoleId(roleId: Long): List<String>
}

/** [SystemQueryRepository] 的 Exposed 实现：直读 sys_* 五张表（列定义见 AuthTables） */
@Repository
@Transactional(readOnly = true)
class ExposedSystemQueryRepository : SystemQueryRepository {
    private val sysUsers = SysUsersTable
    private val sysRoles = SysRolesTable
    private val sysPermissions = SysPermissionsTable
    private val sysUserRoles = SysUserRolesTable
    private val sysRolePermissions = SysRolePermissionsTable

    override fun findAllUsers(): List<SystemUserRow> =
        sysUsers
            .selectAll()
            .orderBy(sysUsers.id to SortOrder.ASC)
            .map(::toUserRow)

    override fun findAllRoles(): List<SystemRoleRow> =
        sysRoles
            .selectAll()
            .orderBy(sysRoles.id to SortOrder.ASC)
            .map(::toRoleRow)

    override fun findAllPermissions(): List<SystemPermissionRow> =
        sysPermissions
            .selectAll()
            .orderBy(sysPermissions.id to SortOrder.ASC)
            .map(::toPermissionRow)

    override fun findAllUserRoles(): List<SystemUserRoleRow> =
        sysUserRoles
            .selectAll()
            .orderBy(sysUserRoles.id to SortOrder.ASC)
            .map { row ->
                SystemUserRoleRow(
                    userId = row[sysUserRoles.userId],
                    roleId = row[sysUserRoles.roleId],
                )
            }

    override fun findPermissionCodesByRoleId(roleId: Long): List<String> =
        sysPermissions
            .join(sysRolePermissions, JoinType.INNER, additionalConstraint = { sysRolePermissions.permissionId eq sysPermissions.id })
            .selectAll()
            .where { sysRolePermissions.roleId eq roleId }
            .orderBy(sysRolePermissions.id to SortOrder.ASC)
            .map { it[sysPermissions.permissionCode] }

    private fun toUserRow(row: ResultRow): SystemUserRow =
        SystemUserRow(
            id = row[sysUsers.id],
            username = row[sysUsers.username],
            nickname = row[sysUsers.nickname],
            email = row[sysUsers.email],
            phone = row[sysUsers.phone],
            status = row[sysUsers.status],
            // created_at NOT NULL（DEFAULT CURRENT_TIMESTAMP），EPOCH 兜底防御异常行
            createdAt = row[sysUsers.createdAt] ?: Instant.EPOCH,
        )

    private fun toRoleRow(row: ResultRow): SystemRoleRow =
        SystemRoleRow(
            id = row[sysRoles.id],
            roleCode = row[sysRoles.roleCode],
            roleName = row[sysRoles.roleName],
            description = row[sysRoles.description],
            status = row[sysRoles.status],
        )

    private fun toPermissionRow(row: ResultRow): SystemPermissionRow =
        SystemPermissionRow(
            id = row[sysPermissions.id],
            permissionCode = row[sysPermissions.permissionCode],
            permissionName = row[sysPermissions.permissionName],
            resourceType = row[sysPermissions.resourceType],
            resourcePath = row[sysPermissions.resourcePath],
            method = row[sysPermissions.method],
            parentId = row[sysPermissions.parentId],
            sortOrder = row[sysPermissions.sortOrder],
        )
}
