package com.example.ruleengine.controller;

import com.example.ruleengine.model.dto.LoginRequest;
import com.example.ruleengine.model.dto.LoginResponse;
import com.example.ruleengine.model.dto.UserInfoResponse;
import com.example.ruleengine.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证 API 控制器
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * 登录
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("登录请求: username={}", request.getUsername());
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 登出
     * POST /api/v1/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        authService.logout();
        return ResponseEntity.ok().build();
    }

    /**
     * 获取当前登录用户信息
     * GET /api/v1/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> getCurrentUser() {
        UserInfoResponse response = authService.getCurrentUser();
        return ResponseEntity.ok(response);
    }

    /**
     * 检查登录状态
     * GET /api/v1/auth/check
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkLogin() {
        return ResponseEntity.ok(Map.of(
                "loggedIn", true,
                "userId", cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong()
        ));
    }
}
