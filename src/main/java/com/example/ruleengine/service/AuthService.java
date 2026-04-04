package com.example.ruleengine.service;

import cn.dev33.satoken.stp.StpUtil;
import com.example.ruleengine.domain.SysPermission;
import com.example.ruleengine.domain.SysUser;
import com.example.ruleengine.model.dto.LoginRequest;
import com.example.ruleengine.model.dto.LoginResponse;
import com.example.ruleengine.model.dto.UserInfoResponse;
import com.example.ruleengine.repository.SysPermissionRepository;
import com.example.ruleengine.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final SysUserRepository sysUserRepository;
    private final SysPermissionRepository sysPermissionRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 登录
     */
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalArgumentException("账号已被禁用或锁定");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // Sa-Token 登录
        StpUtil.login(user.getId());

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        sysUserRepository.save(user);

        // 获取角色列表
        List<String> roleCodes = sysPermissionRepository.findRoleCodesByUserId(user.getId());

        log.info("用户登录成功: username={}, roles={}", user.getUsername(), roleCodes);

        return LoginResponse.of(
                StpUtil.getTokenValue(),
                user.getUsername(),
                user.getNickname(),
                roleCodes
        );
    }

    /**
     * 登出
     */
    public void logout() {
        StpUtil.logout();
        log.info("用户登出: userId={}", StpUtil.getLoginIdDefaultNull());
    }

    /**
     * 获取当前登录用户信息
     */
    public UserInfoResponse getCurrentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("用户不存在"));

        List<String> roleCodes = sysPermissionRepository.findRoleCodesByUserId(userId);
        List<String> permissionCodes = sysPermissionRepository.findPermissionsByUserId(userId)
                .stream()
                .map(SysPermission::getPermissionCode)
                .toList();

        return UserInfoResponse.of(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getPhone(),
                roleCodes,
                permissionCodes
        );
    }
}
