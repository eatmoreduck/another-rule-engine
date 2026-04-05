package com.example.ruleengine.service;

import cn.dev33.satoken.stp.StpUtil;
import com.example.ruleengine.domain.SysPermission;
import com.example.ruleengine.domain.SysUser;
import com.example.ruleengine.model.dto.LoginRequest;
import com.example.ruleengine.model.dto.LoginResponse;
import com.example.ruleengine.model.dto.UserInfoResponse;
import com.example.ruleengine.repository.SysPermissionRepository;
import com.example.ruleengine.repository.SysUserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** 登录失败次数缓存，key 为 username，value 为失败次数 */
    private final Cache<String, AtomicInteger> loginFailCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .maximumSize(1000)
            .build();

    /** 最大连续失败次数，超过后锁定 30 分钟 */
    private static final int MAX_LOGIN_FAILURES = 5;

    /**
     * 登录
     */
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername();

        // 检查是否已被登录锁定
        AtomicInteger failCount = loginFailCache.getIfPresent(username);
        if (failCount != null && failCount.get() >= MAX_LOGIN_FAILURES) {
            log.warn("账号已被登录锁定: username={}, failCount={}", username, failCount.get());
            throw new IllegalArgumentException("账号已被锁定，请 30 分钟后重试");
        }

        SysUser user = sysUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalArgumentException("账号已被禁用或锁定");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            // 记录登录失败
            recordLoginFailure(username);
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // 登录成功，清除失败计数
        loginFailCache.invalidate(username);

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
     * 记录登录失败次数
     */
    private void recordLoginFailure(String username) {
        AtomicInteger count = loginFailCache.get(username, k -> new AtomicInteger(0));
        int newCount = count.incrementAndGet();
        log.warn("登录失败: username={}, 连续失败次数={}", username, newCount);
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
