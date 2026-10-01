package com.eatmoreduck.ruleengine.engine

import com.github.benmanes.caffeine.cache.stats.CacheStats
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 脚本校验结果。
 */
sealed interface ValidationResult {
    /** 是否通过校验 */
    val valid: Boolean
        get() = this is Success

    /** 校验通过 */
    data object Success : ValidationResult

    /**
     * 校验失败。
     *
     * @property errors 错误信息列表（安全审计错误 / 语法错误，均含明确原因）
     */
    data class Failure(
        val errors: List<String>,
    ) : ValidationResult
}

/**
 * Groovy 沙箱脚本引擎对外门面（零 Spring 依赖，纯 Kotlin + Groovy + Caffeine）。
 *
 * 三个核心能力：
 * 1. [validate] 校验——静态审计（危险 API 检测）+ 沙箱编译（语法 / 白名单校验），不执行
 * 2. [compile] 编译——沙箱编译 + Caffeine 缓存（key = SHA-256(沙箱配置版本 + 脚本文本)）
 * 3. [execute] 执行——注入变量 Map，超时中断，返回脚本结果
 *
 * 防护分层（完整移植旧实现的语义）：
 * - 第一层（编译期）：SecureASTCustomizer 导入白名单 + 接收者类黑名单，见 [SandboxConfiguration]
 * - 第二层（编译前）：[ScriptAuditor] 归一化文本预检危险 API
 * - 第三层（执行期）：独立类加载器隔离 + 虚拟线程执行 + 超时中断（循环体注入检查点）
 *
 * @param sandbox 沙箱编译配置（默认使用集中白名单）
 * @param auditor 静态审计器
 * @param cacheMaximumSize 编译缓存容量上限
 * @param cacheExpireAfterAccess 编译缓存条目空闲过期时间
 * @param defaultExecutionTimeout 默认执行超时（单次 execute 可覆盖）
 */
class GroovyScriptEngine(
    val sandbox: SandboxConfiguration = SandboxConfiguration(),
    val auditor: ScriptAuditor = ScriptAuditor(),
    cacheMaximumSize: Long = SandboxWhitelist.MAX_CACHE_ENTRIES,
    cacheExpireAfterAccess: Duration = SandboxWhitelist.CACHE_EXPIRE_AFTER_ACCESS,
    private val defaultExecutionTimeout: Duration = SandboxWhitelist.DEFAULT_EXECUTION_TIMEOUT,
) : AutoCloseable {
    private val classLoaderProvider = ScriptClassLoaderProvider(sandbox)
    private val cache =
        ScriptCompilationCache(
            classLoaderProvider = classLoaderProvider,
            sandbox = sandbox,
            maximumSize = cacheMaximumSize,
            expireAfterAccess = cacheExpireAfterAccess,
        )

    /** 脚本在虚拟线程上执行：轻量、支持中断、不占用平台线程池 */
    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    /** 编译缓存统计（命中率 / 逐出数等） */
    val cacheStats: CacheStats
        get() = cache.stats

    /** 实际编译次数（缓存命中不计数） */
    val compileCount: Long
        get() = cache.compileCount.get()

    /** 编译缓存当前条目数（近似值） */
    val cacheSize: Long
        get() = cache.size

    /**
     * 校验脚本：先静态审计，再沙箱编译（同时预热编译缓存），不执行脚本。
     */
    fun validate(script: String): ValidationResult {
        val auditResult = auditor.audit(script)
        if (!auditResult.safe) {
            return ValidationResult.Failure(listOf("安全审计失败: ${auditResult.errors.joinToString("; ")}"))
        }
        return try {
            compile(script)
            ValidationResult.Success
        } catch (e: ScriptCompilationException) {
            ValidationResult.Failure(listOf(e.message ?: "脚本编译失败"))
        }
    }

    /**
     * 编译脚本（带缓存）。
     *
     * 编译前先做静态审计（含空脚本、超长脚本检查），任一错误直接拒绝；
     * 缓存命中时返回既有编译产物，不重复编译。
     *
     * @throws ScriptCompilationException 审计未通过或编译失败
     */
    fun compile(script: String): CompiledScript {
        val auditResult = auditor.audit(script)
        auditResult.errors.firstOrNull()?.let { error ->
            throw ScriptCompilationException(error)
        }
        return cache.get(script)
    }

    /**
     * 执行脚本：注入变量 Map，返回脚本结果。
     *
     * 脚本在虚拟线程上运行并受超时保护——超时后向脚本线程发出中断，
     * 循环体内注入的检查点会在下一轮迭代抛出 InterruptedException 终止脚本。
     *
     * @param script 脚本文本
     * @param variables 注入脚本的变量（如特征数据），可包含 null 值
     * @param timeout 执行超时时间（默认 [SandboxWhitelist.DEFAULT_EXECUTION_TIMEOUT]）
     * @return 脚本执行结果
     * @throws ScriptCompilationException 审计未通过或编译失败
     * @throws ScriptTimeoutException 执行超时（脚本已被中断）
     * @throws ScriptExecutionException 脚本运行期异常
     */
    fun execute(
        script: String,
        variables: Map<String, Any?> = emptyMap(),
        timeout: Duration = defaultExecutionTimeout,
    ): Any? {
        val compiled = compile(script)
        val future = executor.submit(Callable { compiled.run(variables) })
        return try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            // 中断脚本线程：循环体每轮迭代都会检查中断标志，可及时退出
            future.cancel(true)
            throw ScriptTimeoutException("脚本执行超时（${timeout.toMillis()}ms），已中断", e)
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is ScriptEngineException -> throw cause
                else -> throw ScriptExecutionException("脚本运行失败: ${cause?.message}", cause)
            }
        }
    }

    /**
     * 丢弃全部编译缓存与隔离类加载器（整体释放脚本编译产物）。
     * 适用于沙箱配置热更新后整体重置，防止 Metaspace 泄漏与类污染。
     */
    fun discardAll() {
        cache.invalidateAll()
        classLoaderProvider.close()
    }

    /** 当前活跃的隔离类加载器数量 */
    val activeClassLoaderCount: Int
        get() = classLoaderProvider.activeCount

    /** 释放全部资源：编译缓存、类加载器与执行线程池 */
    override fun close() {
        discardAll()
        executor.shutdownNow()
    }
}
