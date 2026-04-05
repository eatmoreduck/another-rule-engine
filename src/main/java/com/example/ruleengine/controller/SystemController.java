package com.example.ruleengine.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.ruleengine.model.dto.*;
import com.example.ruleengine.service.RoleManagementService;
import com.example.ruleengine.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统管理 API 控制器
 * 用户管理、角色管理、权限管理
 */
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Slf4j
@SaCheckLogin
public class SystemController {

    private final UserManagementService userManagementService;
    private final RoleManagementService roleManagementService;

    // ==================== 用户管理 ====================

    /**
     * 获取用户列表
     * GET /api/v1/system/users
     */
    @GetMapping("/users")
    @SaCheckPermission("api:system:user:view")
    public ResponseEntity<List<UserDTO>> listUsers() {
        return ResponseEntity.ok(userManagementService.listUsers());
    }

    /**
     * 创建用户
     * POST /api/v1/system/users
     */
    @PostMapping("/users")
    @SaCheckPermission("api:system:user:manage")
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("创建用户: username={}", request.getUsername());
        return ResponseEntity.ok(userManagementService.createUser(request));
    }

    /**
     * 更新用户
     * PUT /api/v1/system/users/{id}
     */
    @PutMapping("/users/{id}")
    @SaCheckPermission("api:system:user:manage")
    public ResponseEntity<UserDTO> updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request) {
        log.info("更新用户: id={}", id);
        return ResponseEntity.ok(userManagementService.updateUser(id, request));
    }

    /**
     * 更新用户状态（启用/禁用）
     * PUT /api/v1/system/users/{id}/status
     */
    @PutMapping("/users/{id}/status")
    @SaCheckPermission("api:system:user:manage")
    public ResponseEntity<UserDTO> updateUserStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        log.info("更新用户状态: id={}, status={}", id, status);
        return ResponseEntity.ok(userManagementService.updateUserStatus(id, status));
    }

    /**
     * 重置密码
     * PUT /api/v1/system/users/{id}/reset-password
     */
    @PutMapping("/users/{id}/reset-password")
    @SaCheckPermission("api:system:user:manage")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable Long id) {
        log.info("重置用户密码: id={}", id);
        userManagementService.resetPassword(id);
        return ResponseEntity.ok(Map.of("message", "密码已重置为默认密码"));
    }

    // ==================== 角色管理 ====================

    /**
     * 获取角色列表
     * GET /api/v1/system/roles
     */
    @GetMapping("/roles")
    @SaCheckPermission("api:system:role:view")
    public ResponseEntity<List<RoleDTO>> listRoles() {
        return ResponseEntity.ok(roleManagementService.listRoles());
    }

    /**
     * 更新角色权限
     * PUT /api/v1/system/roles/{id}/permissions
     */
    @PutMapping("/roles/{id}/permissions")
    @SaCheckPermission("api:system:role:manage")
    public ResponseEntity<RoleDTO> updateRolePermissions(
            @PathVariable Long id,
            @RequestBody UpdateRolePermissionsRequest request) {
        log.info("更新角色权限: roleId={}, permissionCount={}", id,
                request.getPermissionIds() != null ? request.getPermissionIds().size() : 0);
        return ResponseEntity.ok(roleManagementService.updateRolePermissions(id, request));
    }

    // ==================== 权限管理 ====================

    /**
     * 获取权限列表
     * GET /api/v1/system/permissions
     */
    @GetMapping("/permissions")
    @SaCheckPermission("api:system:role:view")
    public ResponseEntity<List<PermissionDTO>> listPermissions() {
        return ResponseEntity.ok(roleManagementService.listPermissions());
    }
}
