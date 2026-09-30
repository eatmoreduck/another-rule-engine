package com.example.ruleengine.decision.auth

import cn.dev33.satoken.stp.StpInterface
import org.springframework.stereotype.Component

/**
 * Sa-Token 权限/角色数据源（决策侧）：@SaCheckPermission("api:decision:execute")
 * 注解校验的取值入口，行为与旧 StpInterfaceImpl 一致（权限码与角色码均来自 sys_* 关联表）。
 */
@Component
class DecisionStpInterface(
    private val authRepository: DecisionAuthRepository,
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
