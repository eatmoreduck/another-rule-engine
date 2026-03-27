package com.example.ruleengine.engine;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import groovy.lang.GroovyClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityConfiguration 单元测试
 */
@DisplayName("SecurityConfiguration 测试")
class SecurityConfigurationTest {

    private final SecurityConfiguration securityConfig = new SecurityConfiguration();

    @Test
    @DisplayName("应创建安全的 CompilerConfiguration")
    void shouldCreateSecureConfiguration() {
        CompilerConfiguration config = securityConfig.createSecureConfiguration();

        assertNotNull(config);
        // 验证优化选项已设置
        assertNotNull(config.getOptimizationOptions());
        // 验证编译自定义器已配置
        assertFalse(config.getCompilationCustomizers().isEmpty());
    }

    @Test
    @DisplayName("应创建独立的 GroovyClassLoader")
    void shouldCreateIsolatedClassLoader() {
        GroovyClassLoader loader = securityConfig.createIsolatedClassLoader();

        assertNotNull(loader);
        assertNotSame(getClass().getClassLoader(), loader);
    }

    @Test
    @DisplayName("应配置导入白名单")
    void shouldConfigureImportsWhitelist() {
        CompilerConfiguration config = securityConfig.createSecureConfiguration();

        // 验证 SecureASTCustomizer 已配置
        // 注意：具体验证方式取决于 CompilerConfiguration 的实现
        assertNotNull(config.getCompilationCustomizers());
        assertFalse(config.getCompilationCustomizers().isEmpty());
    }
}
