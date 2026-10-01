package com.eatmoreduck.ruleengine.admin.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.annotation.SaCheckPermission
import com.eatmoreduck.ruleengine.admin.dto.SystemPermissionResponse
import com.eatmoreduck.ruleengine.admin.dto.SystemRoleResponse
import com.eatmoreduck.ruleengine.admin.dto.SystemUserResponse
import com.eatmoreduck.ruleengine.admin.system.SystemQueryService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 系统管理查询 REST API 控制器（路径/响应字段/权限码与旧 SystemController 的三个
 * 只读端点一致：/api/v1/system 下的 users / roles / permissions）。
 *
 * 旧控制器的写操作族（创建/更新用户、角色权限配置、团队管理）未在本批迁移范围，
 * 前端对应操作暂不可用；审计写入侧的规划见 .planning/STATE.md。
 */
@RestController
@RequestMapping("/api/v1/system")
@SaCheckLogin
class SystemController(
    private val systemQueryService: SystemQueryService,
) {
    private val log = LoggerFactory.getLogger(SystemController::class.java)

    /** 用户列表：GET /api/v1/system/users */
    @GetMapping("/users")
    @SaCheckPermission("api:system:user:view")
    fun listUsers(): ResponseEntity<List<SystemUserResponse>> {
        log.debug("查询系统用户列表")
        return ResponseEntity.ok(systemQueryService.listUsers())
    }

    /** 角色列表：GET /api/v1/system/roles */
    @GetMapping("/roles")
    @SaCheckPermission("api:system:role:view")
    fun listRoles(): ResponseEntity<List<SystemRoleResponse>> {
        log.debug("查询系统角色列表")
        return ResponseEntity.ok(systemQueryService.listRoles())
    }

    /** 权限清单：GET /api/v1/system/permissions */
    @GetMapping("/permissions")
    @SaCheckPermission("api:system:role:view")
    fun listPermissions(): ResponseEntity<List<SystemPermissionResponse>> {
        log.debug("查询系统权限清单")
        return ResponseEntity.ok(systemQueryService.listPermissions())
    }
}
