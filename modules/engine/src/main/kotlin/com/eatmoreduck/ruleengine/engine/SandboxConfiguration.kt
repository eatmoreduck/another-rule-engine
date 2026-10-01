package com.eatmoreduck.ruleengine.engine

import groovy.lang.GroovyClassLoader
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.SecureASTCustomizer

/**
 * Groovy 沙箱编译配置（第一层防护：编译期拦截）。
 *
 * 移植旧 SecurityConfiguration 的全部语义：
 * - SecureASTCustomizer 导入白名单（[SandboxWhitelist.ALLOWED_IMPORTS]）
 * - 星号导入白名单（[SandboxWhitelist.ALLOWED_STAR_IMPORTS]）
 * - 静态导入 / 静态星号导入禁止（空白名单）
 * - 接收者类黑名单（[SandboxWhitelist.BLACKLISTED_RECEIVER_CLASSES]）
 * - 间接导入检查关闭（indirectImportCheckEnabled=false：开启会误拦 collect/sum 等
 *   正常 Groovy GDK 方法调用，改由 [ScriptAuditor] 正则审计补充防护）
 * - PARALLEL_PARSE 并行解析优化
 *
 * 在此之上新增：[LoopInterruptGuard]——向循环体注入中断检查点，
 * 使执行超时能真正打断 while(true) 类死循环脚本。
 *
 * @property version 配置版本号，参与编译缓存 key 计算；白名单/禁止项变更时递增
 * @property parentClassLoader 沙箱类加载器的父加载器（宿主类从这里可见）
 */
class SandboxConfiguration(
    val version: String = SandboxWhitelist.SANDBOX_CONFIG_VERSION,
    val parentClassLoader: ClassLoader = SandboxConfiguration::class.java.classLoader,
) {
    /**
     * 创建带安全定制器的 CompilerConfiguration。
     * 每次编译使用独立配置实例，避免多个脚本共享可变状态。
     */
    fun createSecureConfiguration(): CompilerConfiguration {
        val config = CompilerConfiguration()

        // 并行解析优化：多线程编译时提升吞吐
        config.optimizationOptions = mapOf(CompilerConfiguration.PARALLEL_PARSE to true)

        val secureCustomizer = SecureASTCustomizer()
        // 导入白名单（不设黑名单：SecureASTCustomizer 不允许白名单与黑名单同时设置）
        secureCustomizer.importsWhitelist = SandboxWhitelist.ALLOWED_IMPORTS
        // 星号导入白名单
        secureCustomizer.starImportsWhitelist = SandboxWhitelist.ALLOWED_STAR_IMPORTS
        // 禁止静态导入 / 静态星号导入
        secureCustomizer.staticImportsWhitelist = emptyList()
        secureCustomizer.staticStarImportsWhitelist = emptyList()
        // 允许闭包（规则逻辑中使用）与方法定义（规则中可定义辅助方法）
        // （直接调用 setter：isXxx 前缀的 getter 无法映射为 Kotlin 属性语法）
        secureCustomizer.setClosuresAllowed(true)
        secureCustomizer.setMethodDefinitionAllowed(true)
        // 间接导入检查关闭：由 ScriptAuditor 正则审计补充防护
        secureCustomizer.setIndirectImportCheckEnabled(false)
        // 接收者类黑名单（直接调用 setter：该定制器未提供对应 getter，无法用属性语法）
        secureCustomizer.setReceiversClassesBlackList(SandboxWhitelist.BLACKLISTED_RECEIVER_CLASSES)

        // 注意顺序：SecureASTCustomizer（语义分析期）在前；
        // 中断注入（转换期）先改写 AST，随后安全定制器会一并校验注入后的节点。
        config.addCompilationCustomizers(secureCustomizer, LoopInterruptGuard())

        return config
    }

    /**
     * 创建独立的 GroovyClassLoader：脚本编译产物在独立加载器中，
     * 可随 [ScriptClassLoaderProvider] 整体丢弃，防止类污染与 Metaspace 泄漏。
     */
    fun createIsolatedClassLoader(): GroovyClassLoader = GroovyClassLoader(parentClassLoader, createSecureConfiguration())
}
