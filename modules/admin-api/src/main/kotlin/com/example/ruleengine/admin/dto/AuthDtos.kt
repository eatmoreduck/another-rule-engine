package com.example.ruleengine.admin.dto

import jakarta.validation.constraints.NotBlank

/**
 * 登录请求（字段与校验消息与旧 LoginRequest 逐字一致）。
 */
data class LoginRequest(
    @field:NotBlank(message = "用户名不能为空")
    val username: String = "",
    @field:NotBlank(message = "密码不能为空")
    val password: String = "",
)

/**
 * 登录响应（对应旧 LoginResponse）。
 */
data class LoginResponse(
    val token: String,
    val username: String,
    val nickname: String?,
    val roles: List<String>,
)

/**
 * 当前登录用户信息（对应旧 UserInfoResponse）。
 */
data class UserInfoResponse(
    val id: Long,
    val username: String,
    val nickname: String?,
    val email: String?,
    val phone: String?,
    val roles: List<String>,
    val permissions: List<String>,
)

/**
 * 登录状态检查响应（对应旧 /auth/check 的 Map 字段）。
 */
data class LoginCheckResponse(
    val loggedIn: Boolean,
    val userId: Long,
)
