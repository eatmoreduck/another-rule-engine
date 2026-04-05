package com.example.ruleengine.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.ruleengine.model.dto.*;
import com.example.ruleengine.annotation.Auditable;
import com.example.ruleengine.constants.AuditEvent;
import com.example.ruleengine.domain.SysTeam;
import com.example.ruleengine.service.RoleManagementService;
import com.example.ruleengine.service.UserManagementService;
import com.example.ruleengine.service.auth.TeamManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统管理 API 控制器
 * 用户管理、角色管理、权限管理、团队管理
 */
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Slf4j
@SaCheckLogin
public class SystemController {

    private final UserManagementService userManagementService;
    private final RoleManagementService roleManagementService;
    private final TeamManagementService teamManagementService;

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
    @Auditable(event = AuditEvent.USER_CREATE, entityType = "USER", entityIdExpression = "#request.username")
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
    @Auditable(event = AuditEvent.USER_UPDATE, entityType = "USER", entityIdExpression = "#id")
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
    @Auditable(event = AuditEvent.USER_DISABLE, entityType = "USER", entityIdExpression = "#id")
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
    @Auditable(event = AuditEvent.USER_RESET_PASSWORD, entityType = "USER", entityIdExpression = "#id")
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
    @Auditable(event = AuditEvent.ROLE_UPDATE_PERMISSIONS, entityType = "ROLE", entityIdExpression = "#id")
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

    // ==================== 团队管理 ====================

    /**
     * 获取团队列表
     * GET /api/v1/system/teams
     */
    @GetMapping("/teams")
    @SaCheckPermission("api:system:user:view")
    public ResponseEntity<List<SysTeam>> listTeams() {
        return ResponseEntity.ok(teamManagementService.listTeams());
    }

    /**
     * 创建团队
     * POST /api/v1/system/teams
     */
    @PostMapping("/teams")
    @SaCheckPermission("api:system:user:manage")
    @Auditable(event = AuditEvent.TEAM_CREATE, entityType = "TEAM", entityIdExpression = "#body['teamCode']")
    public ResponseEntity<SysTeam> createTeam(@RequestBody Map<String, String> body) {
        String teamCode = body.get("teamCode");
        String teamName = body.get("teamName");
        String description = body.get("description");
        log.info("创建团队: teamCode={}, teamName={}", teamCode, teamName);
        return ResponseEntity.ok(teamManagementService.createTeam(teamCode, teamName, description));
    }

    /**
     * 更新团队
     * PUT /api/v1/system/teams/{id}
     */
    @PutMapping("/teams/{id}")
    @SaCheckPermission("api:system:user:manage")
    @Auditable(event = AuditEvent.TEAM_UPDATE, entityType = "TEAM", entityIdExpression = "#id")
    public ResponseEntity<SysTeam> updateTeam(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        log.info("更新团队: id={}", id);
        return ResponseEntity.ok(teamManagementService.updateTeam(
                id, body.get("teamName"), body.get("description")));
    }

    /**
     * 删除团队
     * DELETE /api/v1/system/teams/{id}
     */
    @DeleteMapping("/teams/{id}")
    @SaCheckPermission("api:system:user:manage")
    @Auditable(event = AuditEvent.TEAM_DELETE, entityType = "TEAM", entityIdExpression = "#id")
    public ResponseEntity<Void> deleteTeam(@PathVariable Long id) {
        log.info("删除团队: id={}", id);
        teamManagementService.deleteTeam(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 分配用户到团队
     * POST /api/v1/system/teams/{teamId}/members
     */
    @PostMapping("/teams/{teamId}/members")
    @SaCheckPermission("api:system:user:manage")
    @Auditable(event = AuditEvent.TEAM_ASSIGN_USER, entityType = "TEAM", entityIdExpression = "#teamId")
    public ResponseEntity<Void> assignUserToTeam(
            @PathVariable Long teamId,
            @RequestBody Map<String, Long> body) {
        Long userId = body.get("userId");
        log.info("分配用户到团队: userId={}, teamId={}", userId, teamId);
        teamManagementService.assignUserToTeam(userId, teamId);
        return ResponseEntity.ok().build();
    }

    /**
     * 从团队移除用户
     * DELETE /api/v1/system/teams/{teamId}/members/{userId}
     */
    @DeleteMapping("/teams/{teamId}/members/{userId}")
    @SaCheckPermission("api:system:user:manage")
    @Auditable(event = AuditEvent.TEAM_REMOVE_USER, entityType = "TEAM", entityIdExpression = "#teamId")
    public ResponseEntity<Void> removeUserFromTeam(
            @PathVariable Long teamId,
            @PathVariable Long userId) {
        log.info("从团队移除用户: userId={}, teamId={}", userId, teamId);
        teamManagementService.removeUserFromTeam(userId, teamId);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取团队的用户ID列表
     * GET /api/v1/system/teams/{teamId}/members
     */
    @GetMapping("/teams/{teamId}/members")
    @SaCheckPermission("api:system:user:view")
    public ResponseEntity<List<Long>> getTeamMembers(@PathVariable Long teamId) {
        return ResponseEntity.ok(teamManagementService.getTeamUserIds(teamId));
    }

    /**
     * 获取用户所属的团队列表
     * GET /api/v1/system/users/{userId}/teams
     */
    @GetMapping("/users/{userId}/teams")
    @SaCheckPermission("api:system:user:view")
    public ResponseEntity<List<SysTeam>> getUserTeams(@PathVariable Long userId) {
        return ResponseEntity.ok(teamManagementService.getUserTeams(userId));
    }
}
