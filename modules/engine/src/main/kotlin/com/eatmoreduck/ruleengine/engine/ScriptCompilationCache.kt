package com.eatmoreduck.ruleengine.engine

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.stats.CacheStats
import groovy.lang.Binding
import groovy.lang.GroovyShell
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.concurrent.atomic.AtomicLong

/**
 * 脚本编译缓存（Caffeine）。
 *
 * - key = SHA-256(沙箱配置版本 + "\n" + 脚本文本)：配置版本变化自动导致全部缓存失效
 * - 缓存命中不重复编译；同 key 并发未命中时只编译一次（Caffeine 按 key 原子加载，
 *   天然防止并发编译风暴）
 * - 条目逐出（容量上限 / 空闲过期）时通过回调关闭其隔离类加载器，防止 Metaspace 泄漏
 * - 命中/未命中统计（recordStats）供监控与测试使用
 */
class ScriptCompilationCache(
    private val classLoaderProvider: ScriptClassLoaderProvider,
    private val sandbox: SandboxConfiguration,
    maximumSize: Long = SandboxWhitelist.MAX_CACHE_ENTRIES,
    expireAfterAccess: Duration = SandboxWhitelist.CACHE_EXPIRE_AFTER_ACCESS,
) : AutoCloseable {
    /** 实际执行的编译次数（缓存命中不计数；测试与监控用） */
    val compileCount: AtomicLong = AtomicLong(0)

    private val cache: Cache<String, CompiledScript> =
        Caffeine
            .newBuilder()
            .maximumSize(maximumSize)
            .expireAfterAccess(expireAfterAccess)
            .recordStats()
            .removalListener<String, CompiledScript> { key, value, _ ->
                // 按实例身份关闭：逐出通知异步到达，防止误关同键新创建的加载器
                if (key != null && value != null) {
                    classLoaderProvider.close(key, value.classLoader)
                }
            }.build()

    /** 缓存统计（命中率、逐出数等） */
    val stats: CacheStats
        get() = cache.stats()

    /** 当前缓存条目数（近似值） */
    val size: Long
        get() = cache.estimatedSize()

    /**
     * 获取已编译脚本；未命中时执行沙箱编译并放入缓存。
     * 同一脚本重复调用必然命中同一条目（返回同一实例）。
     */
    fun get(script: String): CompiledScript {
        val cacheKey = buildCacheKey(script)
        return requireNotNull(cache.get(cacheKey) { compileScript(script, it) }) {
            "脚本编译结果不应为 null: cacheKey=$cacheKey"
        }
    }

    /** 缓存键：SHA-256(沙箱配置版本 + 脚本文本) */
    fun buildCacheKey(script: String): String = sha256(sandbox.version + "\n" + script)

    /** 清空全部缓存条目（各条目的类加载器经回调一并关闭） */
    fun invalidateAll() {
        cache.invalidateAll()
    }

    override fun close() {
        invalidateAll()
    }

    /** 沙箱编译脚本并包装为缓存条目；编译失败时回收本次创建的类加载器 */
    private fun compileScript(
        script: String,
        cacheKey: String,
    ): CompiledScript {
        compileCount.incrementAndGet()
        val classLoader = classLoaderProvider.acquire(cacheKey)
        return try {
            val shell = GroovyShell(classLoader, Binding(), sandbox.createSecureConfiguration())
            val parsed = shell.parse(script)
            CompiledScript(cacheKey, parsed.javaClass, classLoader)
        } catch (e: Exception) {
            classLoaderProvider.close(cacheKey, classLoader)
            throw ScriptCompilationException("脚本编译失败: ${e.message}", e)
        }
    }

    private fun sha256(text: String): String =
        HexFormat
            .of()
            .formatHex(
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(text.toByteArray(Charsets.UTF_8)),
            )
}
