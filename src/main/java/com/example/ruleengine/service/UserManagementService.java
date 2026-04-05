package com.example.ruleengine.service;

import com.example.ruleengine.domain.SysRole;
import com.example.ruleengine.domain.SysUser;
import com.example.ruleengine.domain.SysUserRole;
import com.example.ruleengine.model.dto.CreateUserRequest;
import com.example.ruleengine.model.dto.RoleDTO;
import com.example.ruleengine.model.dto.UpdateUserRequest;
import com.example.ruleengine.model.dto.UserDTO;
import com.example.ruleengine.repository.SysRolePermissionRepository;
import com.example.ruleengine.repository.SysRoleRepository;
import com.example.ruleengine.repository.SysUserRepository;
import com.example.ruleengine.repository.SysUserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserManagementService {

    private final SysUserRepository sysUserRepository;
    private final SysRoleRepository sysRoleRepository;
    private final SysUserRoleRepository sysUserRoleRepository;
    private final SysRolePermissionRepository sysRolePermissionRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final String DEFAULT_PASSWORD = "123456";

    /**
     * 查询所有用户
     */
    public List<UserDTO> listUsers() {
        List<SysUser> users = sysUserRepository.findAll();

        // 批量获取所有用户角色
        List<Long> userIds = users.stream().map(SysUser::getId).toList();
        List<SysUserRole> allUserRoles = userIds.stream()
                .flatMap(userId -> sysUserRoleRepository.findByUserId(userId).stream())
                .toList();

        // 按 userId 分组
        Map<Long, List<Long>> userRoleMap = allUserRoles.stream()
                .collect(Collectors.groupingBy(
                        SysUserRole::getUserId,
                        Collectors.mapping(SysUserRole::getRoleId, Collectors.toList())
                ));

        // 获取所有角色信息
        List<Long> allRoleIds = allUserRoles.stream()
                .map(SysUserRole::getRoleId)
                .distinct()
                .toList();
        Map<Long, SysRole> roleMap = allRoleIds.isEmpty()
                ? Map.of()
                : sysRoleRepository.findByIdIn(allRoleIds).stream()
                        .collect(Collectors.toMap(SysRole::getId, r -> r));

        return users.stream().map(user -> {
            List<Long> roleIds = userRoleMap.getOrDefault(user.getId(), List.of());
            List<RoleDTO> roles = roleIds.stream()
                    .map(roleMap::get)
                    .filter(r -> r != null)
                    .map(r -> RoleDTO.builder()
                            .id(r.getId())
                            .roleCode(r.getRoleCode())
                            .roleName(r.getRoleName())
                            .description(r.getDescription())
                            .status(r.getStatus())
                            .build())
                    .toList();

            return UserDTO.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .nickname(user.getNickname())
                    .email(user.getEmail())
                    .phone(user.getPhone())
                    .status(user.getStatus())
                    .roles(roles)
                    .createdAt(user.getCreatedAt())
                    .build();
        }).toList();
    }

    /**
     * 创建用户
     */
    @Transactional
    public UserDTO createUser(CreateUserRequest request) {
        // 检查用户名是否已存在
        if (sysUserRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("用户名已存在: " + request.getUsername());
        }

        // 创建用户
        SysUser user = SysUser.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .email(request.getEmail())
                .phone(request.getPhone())
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        user = sysUserRepository.save(user);

        // 分配角色
        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            for (Long roleId : request.getRoleIds()) {
                SysUserRole userRole = SysUserRole.builder()
                        .userId(user.getId())
                        .roleId(roleId)
                        .createdAt(LocalDateTime.now())
                        .build();
                sysUserRoleRepository.save(userRole);
            }
        }

        log.info("创建用户成功: username={}, roleIds={}", request.getUsername(), request.getRoleIds());

        // 返回用户信息
        return getUserDTO(user, request.getRoleIds());
    }

    /**
     * 更新用户
     */
    @Transactional
    public UserDTO updateUser(Long id, UpdateUserRequest request) {
        SysUser user = sysUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));

        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        user.setUpdatedAt(LocalDateTime.now());
        user = sysUserRepository.save(user);

        // 更新角色分配
        if (request.getRoleIds() != null) {
            // 先删除旧的角色关联
            List<SysUserRole> existingRoles = sysUserRoleRepository.findByUserId(id);
            sysUserRoleRepository.deleteAll(existingRoles);

            // 再插入新的角色关联
            for (Long roleId : request.getRoleIds()) {
                SysUserRole userRole = SysUserRole.builder()
                        .userId(id)
                        .roleId(roleId)
                        .createdAt(LocalDateTime.now())
                        .build();
                sysUserRoleRepository.save(userRole);
            }
        }

        log.info("更新用户成功: id={}", id);

        List<Long> roleIds = request.getRoleIds() != null
                ? request.getRoleIds()
                : sysUserRoleRepository.findByUserId(id).stream()
                        .map(SysUserRole::getRoleId).toList();

        return getUserDTO(user, roleIds);
    }

    /**
     * 更新用户状态
     */
    @Transactional
    public UserDTO updateUserStatus(Long id, String status) {
        SysUser user = sysUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));

        user.setStatus(status);
        user.setUpdatedAt(LocalDateTime.now());
        user = sysUserRepository.save(user);

        log.info("更新用户状态: id={}, status={}", id, status);

        List<Long> roleIds = sysUserRoleRepository.findByUserId(id).stream()
                .map(SysUserRole::getRoleId).toList();
        return getUserDTO(user, roleIds);
    }

    /**
     * 重置密码
     */
    @Transactional
    public void resetPassword(Long id) {
        SysUser user = sysUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));

        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setUpdatedAt(LocalDateTime.now());
        sysUserRepository.save(user);

        log.info("重置用户密码: id={}, username={}", id, user.getUsername());
    }

    private UserDTO getUserDTO(SysUser user, List<Long> roleIds) {
        List<RoleDTO> roles = List.of();
        if (roleIds != null && !roleIds.isEmpty()) {
            roles = sysRoleRepository.findByIdIn(roleIds).stream()
                    .map(r -> RoleDTO.builder()
                            .id(r.getId())
                            .roleCode(r.getRoleCode())
                            .roleName(r.getRoleName())
                            .description(r.getDescription())
                            .status(r.getStatus())
                            .build())
                    .toList();
        }

        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .roles(roles)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
