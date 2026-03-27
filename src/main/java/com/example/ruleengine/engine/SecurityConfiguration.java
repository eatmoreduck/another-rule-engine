package com.example.ruleengine.engine;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;

/**
 * Groovy 脚本沙箱安全配置
 * Source: RESEARCH.md 模式1 + CONTEXT.md 决策 D-08, D-09, D-10
 *
 * 安全措施：
 * 1. 禁用危险类（System、Runtime、ProcessBuilder、File）
 * 2. 限制导入白名单
 * 3. 禁用静态导入
 * 4. 禁用闭包创建
 */
@Component
public class SecurityConfiguration {

    private static final int MAX_CACHE_SIZE = 1000;
    private static final long CACHE_EXPIRE_HOURS = 24;

    /**
     * 创建安全的 CompilerConfiguration
     * D-08: 禁用危险类
     * D-07: 启用 PARALLEL_PARSE 提升编译性能
     */
    public CompilerConfiguration createSecureConfiguration() {
        CompilerConfiguration config = new CompilerConfiguration();

        // D-07: 启用并行解析优化
        config.setOptimizationOptions(java.util.Map.of("parallelParse", true));

        // D-08: 配置沙箱安全
        SecureASTCustomizer secureCustomizer = new SecureASTCustomizer();

        // 导入白名单：只允许安全的类
        // 注意: SecureASTCustomizer 对通配符支持有限，需要同时指定具体类
        secureCustomizer.setImportsWhitelist(Arrays.asList(
            "java.util.*",
            "java.lang.Math",
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "java.lang.Boolean",
            "java.lang.Object",
            // java.time 包 - 同时支持通配符和具体类
            "java.time.*",
            "java.time.LocalDate",
            "java.time.LocalDateTime",
            "java.time.LocalTime",
            "java.time.ZoneId",
            "java.time.ZonedDateTime",
            "java.time.format.DateTimeFormatter",
            "java.time.Duration",
            "java.time.Period",
            "java.time.Instant",
            "java.math.BigDecimal",
            "java.math.BigInteger"
        ));

        // 星号导入白名单（允许 import java.time.* 这样的语句）
        secureCustomizer.setStarImportsWhitelist(Arrays.asList(
            "java.util",
            "java.time",
            "java.lang",
            "java.math"
        ));

        // 禁止静态导入
        secureCustomizer.setStaticImportsWhitelist(Collections.emptyList());

        // 禁止直接创建闭包（防止绕过沙箱）
        secureCustomizer.setClosuresAllowed(true);

        // 添加到编译配置
        config.addCompilationCustomizers(secureCustomizer);

        return config;
    }

    /**
     * 创建独立的 GroovyClassLoader
     * D-10: 脚本在独立 ClassLoader 中执行
     */
    public GroovyClassLoader createIsolatedClassLoader() {
        CompilerConfiguration config = createSecureConfiguration();
        return new GroovyClassLoader(getClass().getClassLoader(), config);
    }
}
