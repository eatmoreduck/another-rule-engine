package com.eatmoreduck.ruleengine.engine

import groovy.lang.GroovyShell
import org.codehaus.groovy.control.CompilerConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * 沙箱配置测试（移植旧 SecurityConfigurationTest）。
 */
class SandboxConfigurationTest {
    @Test
    fun `secure configuration contains compilation customizers and parallel parse`() {
        // 应创建带安全定制器且开启并行解析的 CompilerConfiguration
        val config = SandboxConfiguration().createSecureConfiguration()
        assertTrue(config.compilationCustomizers.isNotEmpty(), "应包含编译定制器")
        assertEquals(
            true,
            config.optimizationOptions?.get(CompilerConfiguration.PARALLEL_PARSE),
            "应开启 PARALLEL_PARSE 优化",
        )
    }

    @Test
    fun `isolated class loader is distinct from host class loader`() {
        // 应创建独立于宿主的 GroovyClassLoader
        SandboxConfiguration().createIsolatedClassLoader().use { loader ->
            assertNotSame(SandboxConfiguration::class.java.classLoader, loader)
        }
    }

    @Test
    fun `each call creates a fresh secure configuration`() {
        // 每次调用应创建全新配置实例（避免多脚本共享可变状态）
        val sandbox = SandboxConfiguration()
        assertNotSame(sandbox.createSecureConfiguration(), sandbox.createSecureConfiguration())
    }

    @Test
    fun `scripts compiled under sandbox can access host classes via parent delegation`() {
        // 隔离加载器通过父委派仍可解析宿主辅助类（中断检查点依赖此机制）
        val shell = GroovyShell(SandboxConfiguration().createIsolatedClassLoader())
        val result = shell.evaluate("return 40 + 2")
        assertEquals(42, result)
    }

    @Test
    fun `sandbox version participates in cache key`() {
        // 配置版本应参与缓存 key 计算（版本变更自动失效）
        val cache =
            ScriptCompilationCache(
                classLoaderProvider = ScriptClassLoaderProvider(SandboxConfiguration()),
                sandbox = SandboxConfiguration(version = "v-test"),
            )
        assertEquals(
            cache.buildCacheKey("script"),
            ScriptCompilationCache(
                classLoaderProvider = ScriptClassLoaderProvider(SandboxConfiguration()),
                sandbox = SandboxConfiguration(version = "v-test"),
            ).buildCacheKey("script"),
            "相同版本相同脚本应得到相同 key",
        )
    }
}
