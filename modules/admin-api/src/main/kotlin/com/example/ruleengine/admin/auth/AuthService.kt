package com.example.ruleengine.admin.auth

import cn.dev33.satoken.stp.StpUtil
import com.example.ruleengine.admin.dto.LoginRequest
import com.example.ruleengine.admin.dto.LoginResponse
import com.example.ruleengine.admin.dto.UserInfoResponse
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * 认证服务：登录 / 登出 / 当前用户，行为照搬旧 AuthService：
 * - BCrypt 密码校验（spring-security-crypto）；
 * - 连续失败 5 次锁定 30 分钟（Caffeine 缓存，30 分钟写后过期）；
 * - 错误消息逐字一致（前端直接展示 message）；
 * - 登录成功经 Sa-Token 颁发 token（StpUtil.login）并回写最近登录时间。
 */
@Service
class AuthService(
    private val authRepository: AuthRepository,
) {
    private val passwordEncoder = BCryptPasswordEncoder()

    /** 登录失败次数缓存，key 为 username，value 为失败次数 */
    private val loginFailCache: Cache<String, AtomicInteger> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .maximumSize(1000)
            .build()

    @Transactional
    fun login(request: LoginRequest): LoginResponse {
        val username = request.username

        // 已被登录锁定的账号直接拒绝（与旧实现一致：先查锁，再校验）
        val failCount = loginFailCache.getIfPresent(username)
        if (failCount != null && failCount.get() >= MAX_LOGIN_FAILURES) {
            throw IllegalArgumentException("账号已被锁定，请 30 分钟后重试")
        }

        val user =
            authRepository.findByUsername(username)
                ?: throw IllegalArgumentException("用户名或密码错误")

        if (user.status != "ACTIVE") {
            throw IllegalArgumentException("账号已被禁用或锁定")
        }

        if (!passwordEncoder.matches(request.password, user.password)) {
            recordLoginFailure(username)
            throw IllegalArgumentException("用户名或密码错误")
        }

        // 登录成功：清除失败计数、Sa-Token 登录、回写最近登录时间
        loginFailCache.invalidate(username)
        StpUtil.login(user.id)
        authRepository.updateLastLoginAt(user.id, Instant.now())

        val roleCodes = authRepository.findRoleCodes(user.id)
        return LoginResponse(
            token = StpUtil.getTokenValue(),
            username = user.username,
            nickname = user.nickname,
            roles = roleCodes,
        )
    }

    private fun recordLoginFailure(username: String) {
        val count = loginFailCache.get(username) { AtomicInteger(0) }
        count?.incrementAndGet()
    }

    fun logout() {
        val userId = StpUtil.getLoginIdDefaultNull()
        StpUtil.logout()
        if (userId != null) {
            org.slf4j.LoggerFactory
                .getLogger(AuthService::class.java)
                .info("用户登出: userId={}", userId)
        }
    }

    @Transactional(readOnly = true)
    fun getCurrentUser(): UserInfoResponse {
        val userId = StpUtil.getLoginIdAsLong()
        val user =
            authRepository.findById(userId)
                ?: throw IllegalStateException("用户不存在")

        val roleCodes = authRepository.findRoleCodes(userId)
        val permissionCodes = authRepository.findPermissionCodes(userId)

        return UserInfoResponse(
            id = user.id,
            username = user.username,
            nickname = user.nickname,
            email = user.email,
            phone = user.phone,
            roles = roleCodes,
            permissions = permissionCodes,
        )
    }

    companion object {
        /** 最大连续失败次数，超过后锁定 30 分钟 */
        const val MAX_LOGIN_FAILURES: Int = 5
    }
}
