package com.eatmoreduck.ruleengine.admin.system

import com.eatmoreduck.ruleengine.admin.dto.SystemPermissionResponse
import com.eatmoreduck.ruleengine.admin.dto.SystemRoleResponse
import com.eatmoreduck.ruleengine.admin.dto.SystemUserResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 系统管理查询服务（对应旧 UserManagementService.listUsers /
 * RoleManagementService.listRoles + listPermissions 的读取职责）。
 *
 * 装配口径照搬旧实现：
 * - 用户列表：批量取用户-角色关联与角色行后在内存组装（嵌套角色的
 *   permissionCodes 为 null，与旧 listUsers 的序列化形态一致）；
 * - 角色列表：每个角色带其权限码列表；
 * - 权限清单：全量权限行（含菜单/按钮/API 三类，供前端权限树构建）。
 */
@Service
class SystemQueryService(
    private val systemQueryRepository: SystemQueryRepository,
) {
    /** 用户列表（含角色关联） */
    @Transactional(readOnly = true)
    fun listUsers(): List<SystemUserResponse> {
        val users = systemQueryRepository.findAllUsers()
        val roleRows = systemQueryRepository.findAllRoles().associateBy { it.id }
        val rolesByUser = systemQueryRepository.findAllUserRoles().groupBy({ it.userId }, { it.roleId })

        return users.map { user ->
            SystemUserResponse(
                id = user.id,
                username = user.username,
                nickname = user.nickname,
                email = user.email,
                phone = user.phone,
                status = user.status,
                roles =
                    (rolesByUser[user.id]).orEmpty().mapNotNull { roleId ->
                        roleRows[roleId]?.let { role -> toRoleResponse(role, permissionCodes = null) }
                    },
                createdAt = user.createdAt,
            )
        }
    }

    /** 角色列表（含各角色的权限码列表） */
    @Transactional(readOnly = true)
    fun listRoles(): List<SystemRoleResponse> =
        systemQueryRepository.findAllRoles().map { role ->
            toRoleResponse(role, permissionCodes = systemQueryRepository.findPermissionCodesByRoleId(role.id))
        }

    /** 权限清单（全量，id 升序） */
    @Transactional(readOnly = true)
    fun listPermissions(): List<SystemPermissionResponse> =
        systemQueryRepository.findAllPermissions().map { p ->
            SystemPermissionResponse(
                id = p.id,
                permissionCode = p.permissionCode,
                permissionName = p.permissionName,
                resourceType = p.resourceType,
                resourcePath = p.resourcePath,
                method = p.method,
                parentId = p.parentId,
                sortOrder = p.sortOrder,
            )
        }

    private fun toRoleResponse(
        role: SystemRoleRow,
        permissionCodes: List<String>?,
    ): SystemRoleResponse =
        SystemRoleResponse(
            id = role.id,
            roleCode = role.roleCode,
            roleName = role.roleName,
            description = role.description,
            status = role.status,
            permissionCodes = permissionCodes,
        )
}
