package com.eatmoreduck.ruleengine.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 编译缓存测试：缓存命中不重复编译、key 构成、整体丢弃与类加载器回收。
 */
class ScriptCompilationCacheTest {
    /** 轮询等待条件成立（Caffeine 容量维护与逐出通知为异步执行） */
    private fun awaitUntil(
        timeoutMs: Long = 5000,
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (!condition() && System.nanoTime() < deadline) {
            Thread.sleep(20)
        }
        assertTrue(condition(), "等待条件超时（${timeoutMs}ms）")
    }

    @Test
    fun `same script compiles once and returns the same instance`() {
        // 同一脚本二次编译应命中缓存：返回同一实例且只编译一次
        GroovyScriptEngine().use { engine ->
            val first = engine.compile("return 1 + 1")
            val second = engine.compile("return 1 + 1")
            assertSame(first, second)
            assertEquals(1, engine.compileCount)
            assertTrue(engine.cacheStats.hitCount() >= 1, "缓存命中统计应增长: ${engine.cacheStats}")
        }
    }

    @Test
    fun `cache hit does not recompile and execution still works`() {
        // 缓存命中后执行结果仍正确
        GroovyScriptEngine().use { engine ->
            val script = "return x * 2"
            assertEquals(10, engine.execute(script, mapOf("x" to 5)))
            assertEquals(20, engine.execute(script, mapOf("x" to 10)))
            assertEquals(1, engine.compileCount, "同脚本多次执行只应编译一次")
        }
    }

    @Test
    fun `different scripts get different cache entries`() {
        // 不同脚本应产生不同缓存条目
        GroovyScriptEngine().use { engine ->
            val first = engine.compile("return 1")
            val second = engine.compile("return 2")
            assertNotSame(first, second)
            assertEquals(2, engine.compileCount)
            assertEquals(2, engine.cacheSize)
        }
    }

    @Test
    fun `cache key is sha256 of sandbox version plus script`() {
        // 缓存键应为 64 位十六进制 SHA-256，且配置版本参与计算
        val cache =
            ScriptCompilationCache(
                classLoaderProvider = ScriptClassLoaderProvider(SandboxConfiguration()),
                sandbox = SandboxConfiguration(version = "1"),
            )
        val keyA1 = cache.buildCacheKey("a")
        val keyA1Again = cache.buildCacheKey("a")
        assertEquals(keyA1, keyA1Again, "相同输入应得到相同 key")
        assertTrue(keyA1.matches(Regex("[0-9a-f]{64}")), "key 应为 64 位小写十六进制: $keyA1")

        val cacheOtherVersion =
            ScriptCompilationCache(
                classLoaderProvider = ScriptClassLoaderProvider(SandboxConfiguration()),
                sandbox = SandboxConfiguration(version = "2"),
            )
        assertNotEquals(keyA1, cacheOtherVersion.buildCacheKey("a"), "沙箱版本不同 key 必须不同")
    }

    @Test
    fun `discardAll closes isolated class loaders and forces recompile`() {
        // 整体丢弃应关闭全部隔离类加载器；再次编译会创建新产物
        GroovyScriptEngine().use { engine ->
            engine.compile("return 1")
            engine.compile("return 2")
            assertTrue(engine.activeClassLoaderCount >= 2, "应有对应的隔离类加载器: ${engine.activeClassLoaderCount}")

            engine.discardAll()
            assertEquals(0, engine.activeClassLoaderCount, "丢弃后不应残留活跃类加载器")

            // 丢弃后重新编译：产生新的编译产物（新类加载器）
            val recompiled = engine.compile("return 1")
            assertEquals(3, engine.compileCount)
            assertEquals(1, engine.activeClassLoaderCount)
            assertEquals(1, recompiled.run())
        }
    }

    @Test
    fun `evicted entries release their class loaders`() {
        // 容量逐出时应回收对应类加载器（防止 Metaspace 泄漏）。
        // 注意：Caffeine 的容量维护与逐出通知异步执行，断言需轮询等待。
        GroovyScriptEngine(cacheMaximumSize = 1).use { engine ->
            engine.compile("return 1")
            engine.compile("return 2") // 逐出第一条
            awaitUntil { engine.cacheSize <= 1 }
            awaitUntil { engine.activeClassLoaderCount <= 1 }

            // 重新编译被逐出的脚本会再次创建加载器
            engine.compile("return 1")
            assertEquals(3, engine.compileCount)
            awaitUntil { engine.activeClassLoaderCount <= 1 }
        }
    }

    @Test
    fun `close releases everything`() {
        // close() 应同时释放缓存与类加载器
        val engine = GroovyScriptEngine()
        engine.compile("return 1")
        engine.close()
        assertEquals(0, engine.activeClassLoaderCount)
        assertEquals(0, engine.cacheSize)
    }
}
