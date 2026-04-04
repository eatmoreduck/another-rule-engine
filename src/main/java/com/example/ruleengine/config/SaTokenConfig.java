package com.example.ruleengine.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置
 * Phase 1: 注册拦截器但仅用于认证相关路由，现有 API 不做登录校验
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Phase 1: 仅对 /api/v1/auth/me 和 /api/v1/auth/logout 做登录校验
        // 其他所有路由不拦截，确保现有功能不受影响
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/api/v1/auth/me", "/api/v1/auth/logout");
    }
}
