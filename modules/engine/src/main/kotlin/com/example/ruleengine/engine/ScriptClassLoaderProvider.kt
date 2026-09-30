package com.example.ruleengine.engine

import groovy.lang.GroovyClassLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * 脚本类加载器管理（移植旧 ClassLoaderManager 思路并强化）。
 *
 * 与旧实现的差异：
 * - 旧实现用 WeakReference 跟踪加载器，可能在脚本仍在使用时被 GC 静默回收，
 *   也可能因无人清理而泄漏；这里改为强引用 + 与编译缓存同生命周期，
 *   只在本组件显式丢弃时关闭。
 * - 旧实现按 ruleId 共享加载器；这里按缓存键（沙箱版本+脚本哈希）每条编译产物
 *   独享一个 GroovyClassLoader，逐出/重置时整组类一次性卸载，
 *   防止 Metaspace 泄漏与类污染。
 */
class ScriptClassLoaderProvider(
    private val sandbox: SandboxConfiguration,
) : AutoCloseable {
    private val loaders = ConcurrentHashMap<String, GroovyClassLoader>()

    /** 当前活跃（未关闭）的隔离类加载器数量 */
    val activeCount: Int
        get() = loaders.size

    /**
     * 获取（或创建）指定缓存键的隔离类加载器。
     * 同一缓存键只会创建一个加载器（computeIfAbsent 保证原子性）。
     */
    fun acquire(cacheKey: String): GroovyClassLoader = loaders.computeIfAbsent(cacheKey) { sandbox.createIsolatedClassLoader() }

    /** 关闭并移除指定缓存键的类加载器（幂等：键不存在时无操作） */
    fun close(cacheKey: String) {
        loaders.remove(cacheKey)?.closeQuietly()
    }

    /**
     * 关闭指定缓存键的类加载器，但仅当映射仍指向该实例时才移除键。
     * Caffeine 的逐出通知异步到达，用于防止「逐出通知误关同键新创建的加载器」竞态。
     */
    fun close(
        cacheKey: String,
        loader: GroovyClassLoader,
    ) {
        loaders.remove(cacheKey, loader)
        loader.closeQuietly()
    }

    /** 关闭全部隔离类加载器（整体丢弃所有脚本编译产物） */
    override fun close() {
        loaders.values.forEach { loader -> loader.closeQuietly() }
        loaders.clear()
    }

    private fun GroovyClassLoader.closeQuietly() {
        try {
            close()
        } catch (_: Exception) {
            // 关闭失败不影响主流程：类加载器随引用消失后仍可被 GC 回收
        }
    }
}
