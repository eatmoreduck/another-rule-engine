package com.example.ruleengine.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置
 * Phase 2: 认证路由 + 系统管理路由需要登录校验，其他 API 暂不拦截
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Phase 2: 认证相关 + 系统管理路由需要登录校验
        // 其他业务 API 暂不拦截（Phase 5 全面收紧）
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns(
                        "/api/v1/auth/me",
                        "/api/v1/auth/logout",
                        "/api/v1/system/**"
                );
    }
}
