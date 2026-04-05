package com.example.ruleengine.service;

import com.example.ruleengine.domain.SysPermission;
import com.example.ruleengine.domain.SysRole;
import com.example.ruleengine.domain.SysRolePermission;
import com.example.ruleengine.model.dto.PermissionDTO;
import com.example.ruleengine.model.dto.RoleDTO;
import com.example.ruleengine.model.dto.UpdateRolePermissionsRequest;
import com.example.ruleengine.repository.SysPermissionRepository;
import com.example.ruleengine.repository.SysRolePermissionRepository;
import com.example.ruleengine.repository.SysRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 角色管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoleManagementService {

    private final SysRoleRepository sysRoleRepository;
    private final SysRolePermissionRepository sysRolePermissionRepository;
    private final SysPermissionRepository sysPermissionRepository;

    /**
     * 查询所有角色（含权限码列表）
     */
    public List<RoleDTO> listRoles() {
        List<SysRole> roles = sysRoleRepository.findAll();

        return roles.stream().map(role -> {
            List<SysRolePermission> rolePerms = sysRolePermissionRepository.findByRoleId(role.getId());
            List<String> permissionCodes = rolePerms.stream()
                    .map(rp -> sysPermissionRepository.findById(rp.getPermissionId()))
                    .filter(opt -> opt.isPresent())
                    .map(opt -> opt.get().getPermissionCode())
                    .toList();

            return RoleDTO.builder()
                    .id(role.getId())
                    .roleCode(role.getRoleCode())
                    .roleName(role.getRoleName())
                    .description(role.getDescription())
                    .status(role.getStatus())
                    .permissionCodes(permissionCodes)
                    .build();
        }).toList();
    }

    /**
     * 更新角色权限
     */
    @Transactional
    public RoleDTO updateRolePermissions(Long roleId, UpdateRolePermissionsRequest request) {
        SysRole role = sysRoleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + roleId));

        // 先删除旧的权限关联
        List<SysRolePermission> existingPerms = sysRolePermissionRepository.findByRoleId(roleId);
        sysRolePermissionRepository.deleteAll(existingPerms);

        // 再插入新的权限关联
        if (request.getPermissionIds() != null) {
            for (Long permissionId : request.getPermissionIds()) {
                SysRolePermission rolePermission = SysRolePermission.builder()
                        .roleId(roleId)
                        .permissionId(permissionId)
                        .createdAt(LocalDateTime.now())
                        .build();
                sysRolePermissionRepository.save(rolePermission);
            }
        }

        log.info("更新角色权限: roleId={}, permissionCount={}", roleId,
                request.getPermissionIds() != null ? request.getPermissionIds().size() : 0);

        List<String> permissionCodes = (request.getPermissionIds() != null ? request.getPermissionIds() : List.<Long>of())
                .stream()
                .map(pid -> sysPermissionRepository.findById(pid))
                .filter(opt -> opt.isPresent())
                .map(opt -> opt.get().getPermissionCode())
                .toList();

        return RoleDTO.builder()
                .id(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .status(role.getStatus())
                .permissionCodes(permissionCodes)
                .build();
    }

    /**
     * 查询所有权限
     */
    public List<PermissionDTO> listPermissions() {
        List<SysPermission> permissions = sysPermissionRepository.findAll();
        return permissions.stream()
                .map(p -> PermissionDTO.builder()
                        .id(p.getId())
                        .permissionCode(p.getPermissionCode())
                        .permissionName(p.getPermissionName())
                        .resourceType(p.getResourceType())
                        .resourcePath(p.getResourcePath())
                        .method(p.getMethod())
                        .parentId(p.getParentId())
                        .sortOrder(p.getSortOrder())
                        .build())
                .toList();
    }
}
