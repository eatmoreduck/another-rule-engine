package com.example.ruleengine.admin.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.stp.StpUtil
import com.example.ruleengine.admin.auth.AuthService
import com.example.ruleengine.admin.dto.LoginCheckResponse
import com.example.ruleengine.admin.dto.LoginRequest
import com.example.ruleengine.admin.dto.LoginResponse
import com.example.ruleengine.admin.dto.UserInfoResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 认证 API 控制器（路径与旧 AuthController 一致：/api/v1/auth 全族路径）。。
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {
    private val log = LoggerFactory.getLogger(AuthController::class.java)

    /** 登录：POST /api/v1/auth/login（白名单，无需登录） */
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): ResponseEntity<LoginResponse> {
        log.info("登录请求: username={}", request.username)
        return ResponseEntity.ok(authService.login(request))
    }

    /** 登出：POST /api/v1/auth/logout */
    @PostMapping("/logout")
    @SaCheckLogin
    fun logout(): ResponseEntity<Void> {
        authService.logout()
        return ResponseEntity.ok().build()
    }

    /** 当前登录用户信息：GET /api/v1/auth/me */
    @GetMapping("/me")
    @SaCheckLogin
    fun getCurrentUser(): ResponseEntity<UserInfoResponse> = ResponseEntity.ok(authService.getCurrentUser())

    /** 登录状态检查：GET /api/v1/auth/check */
    @GetMapping("/check")
    @SaCheckLogin
    fun checkLogin(): ResponseEntity<LoginCheckResponse> =
        ResponseEntity.ok(LoginCheckResponse(loggedIn = true, userId = StpUtil.getLoginIdAsLong()))
}
