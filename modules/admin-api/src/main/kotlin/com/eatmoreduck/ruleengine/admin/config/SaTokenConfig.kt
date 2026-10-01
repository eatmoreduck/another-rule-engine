package com.eatmoreduck.ruleengine.admin.config

import cn.dev33.satoken.interceptor.SaInterceptor
import cn.dev33.satoken.stp.StpUtil
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Sa-Token 登录拦截配置（照搬旧 SaTokenConfig）：
 * - 所有 /api/v1 前缀路径需要登录（白名单除外），细粒度权限由各 Controller 的 @SaCheckPermission 承担；
 * - 经 sa-token.auth-enabled=false 整体禁用（测试环境）。
 */
@Configuration
@ConditionalOnProperty(name = ["sa-token.auth-enabled"], havingValue = "true", matchIfMissing = true)
class SaTokenConfig : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry
            .addInterceptor(SaInterceptor { StpUtil.checkLogin() })
            .addPathPatterns("/api/v1/**")
            .excludePathPatterns(
                "/api/v1/auth/login",
                "/api/v1/health",
                "/actuator/**",
                "/error",
            )
    }
}
