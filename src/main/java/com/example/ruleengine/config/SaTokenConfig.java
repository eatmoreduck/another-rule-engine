package com.example.ruleengine.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置
 * 全面权限校验 — 所有 /api/v1/** 需要登录（白名单除外）
 * 权限细粒度控制由各 Controller 方法上的 @SaCheckPermission 注解实现
 * 通过 sa-token.auth-enabled=false 禁用（测试环境）
 */
@Configuration
@ConditionalOnProperty(name = "sa-token.auth-enabled", havingValue = "true", matchIfMissing = true)
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/health",
                        "/actuator/**",
                        "/error"
                );
    }
}
