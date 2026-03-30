package com.example.ruleengine.engine;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Groovy 脚本沙箱安全配置
 * Source: RESEARCH.md 模式1 + CONTEXT.md 决策 D-08, D-09, D-10
 *
 * 安全措施（分层防护）：
 * 第一层 - SecureASTCustomizer 编译期拦截：
 *   1. 导入白名单（importsWhitelist）- 只允许安全的类被导入
 *   2. 星号导入白名单（starImportsWhitelist）
 *   3. 静态导入禁止
 *   4. 接收者类黑名单（receiversClassesBlackList）- 禁止对危险类调用方法
 *
 * 第二层 - SecurityAuditService 运行前审计：
 *   5. 正则表达式扫描危险模式（构造函数调用、反射、文件操作等）
 *   6. 嵌套深度检查、脚本长度限制
 *
 * 注意：SecureASTCustomizer 不允许同时设置白名单和黑名单。
 * indirectImportCheckEnabled=true 会拦截大量正常的 Groovy GDK 方法调用，
 * 因此不启用，改由 SecurityAuditService 的正则审计补充。
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

        configureImportWhitelist(secureCustomizer);
        configureReceiverBlacklist(secureCustomizer);

        // indirectImportCheckEnabled 会误拦截大量正常 Groovy 方法调用（如 collect、sum），
        // 因此不启用，安全审计由 SecurityAuditService 补充
        secureCustomizer.setIndirectImportCheckEnabled(false);

        // 禁止静态导入
        secureCustomizer.setStaticImportsWhitelist(Collections.emptyList());

        // 禁止静态星号导入
        secureCustomizer.setStaticStarImportsWhitelist(Collections.emptyList());

        // 允许闭包（规则逻辑中可能使用）
        secureCustomizer.setClosuresAllowed(true);

        // 允许方法定义（规则中可能定义辅助方法）
        secureCustomizer.setMethodDefinitionAllowed(true);

        // 添加到编译配置
        config.addCompilationCustomizers(secureCustomizer);

        return config;
    }

    /**
     * 配置导入白名单
     * 只有在此列表中的导入才是允许的
     * 不设置导入黑名单，因为 SecureASTCustomizer 不允许同时设置两者
     */
    private void configureImportWhitelist(SecureASTCustomizer secureCustomizer) {
        secureCustomizer.setImportsWhitelist(Arrays.asList(
            "java.util.*",
            "java.lang.Math",
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Boolean",
            "java.lang.Object",
            "java.lang.Character",
            "java.lang.Byte",
            "java.lang.Short",
            "java.lang.Number",
            "java.lang.CharSequence",
            "java.lang.Comparable",
            "java.lang.Iterable",
            // java.time 包
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
            // java.math 包
            "java.math.BigDecimal",
            "java.math.BigInteger",
            "java.math.RoundingMode"
        ));

        // 星号导入白名单
        secureCustomizer.setStarImportsWhitelist(Arrays.asList(
            "java.util",
            "java.time",
            "java.lang",
            "java.math"
        ));
    }

    /**
     * 配置接收者类黑名单
     * 禁止对以下类实例调用任何方法
     *
     * 注意：使用 receiversClassesBlackList（基于 Class 对象匹配），
     * 这比 disallowedReceivers（基于字符串匹配）更可靠
     */
    @SuppressWarnings("unchecked")
    private void configureReceiverBlacklist(SecureASTCustomizer secureCustomizer) {
        List<Class> receiverBlacklist = Arrays.asList(
            // 系统操作
            java.lang.System.class,
            java.lang.Runtime.class,
            java.lang.ProcessBuilder.class,
            java.lang.Process.class,
            // 反射操作
            java.lang.Class.class,
            // 线程操作
            java.lang.Thread.class,
            java.lang.ThreadGroup.class,
            // ClassLoader 操作
            java.lang.ClassLoader.class,
            java.net.URLClassLoader.class
        );

        secureCustomizer.setReceiversClassesBlackList(receiverBlacklist);
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
