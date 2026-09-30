package com.example.ruleengine.admin.auth

import cn.dev33.satoken.stp.StpInterface
import org.springframework.stereotype.Component

/**
 * Sa-Token 权限/角色数据源：@SaCheckPermission / @SaCheckRole 注解校验的取值入口。
 * 行为与旧 StpInterfaceImpl 一致（权限码与角色码均来自 sys_* 关联表）。
 */
@Component
class StpInterfaceImpl(
    private val authRepository: AuthRepository,
) : StpInterface {
    override fun getPermissionList(
        loginId: Any,
        loginType: String,
    ): List<String> = authRepository.findPermissionCodes(java.lang.Long.valueOf(loginId.toString()))

    override fun getRoleList(
        loginId: Any,
        loginType: String,
    ): List<String> = authRepository.findRoleCodes(java.lang.Long.valueOf(loginId.toString()))
}
