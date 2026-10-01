package com.eatmoreduck.ruleengine.admin.dto

import java.time.Instant

/**
 * 用户信息响应体（对应旧 UserDTO：用户管理页消费）。
 *
 * [roles] 为关联查询出的角色（sys_user_role → sys_role）；嵌套角色的
 * [SystemRoleResponse.permissionCodes] 为 null——旧 listUsers 构建 RoleDTO 时
 * 未填充该字段，序列化为 null，此处保持一致。
 */
data class SystemUserResponse(
    val id: Long,
    val username: String,
    val nickname: String?,
    val email: String?,
    val phone: String?,
    val status: String,
    val roles: List<SystemRoleResponse>,
    val createdAt: Instant?,
)

/**
 * 角色响应体（对应旧 RoleDTO：角色管理页消费）。
 */
data class SystemRoleResponse(
    val id: Long,
    val roleCode: String,
    val roleName: String,
    val description: String?,
    val status: String,
    /** 角色拥有的权限码列表（/system/roles 返回；嵌套在用户响应中时为 null，与旧一致） */
    val permissionCodes: List<String>?,
)

/**
 * 权限响应体（对应旧 PermissionDTO：角色管理页的权限配置树消费）。
 */
data class SystemPermissionResponse(
    val id: Long,
    val permissionCode: String,
    val permissionName: String,
    /** MENU / BUTTON / API */
    val resourceType: String,
    val resourcePath: String?,
    val method: String?,
    val parentId: Long?,
    val sortOrder: Int,
)
