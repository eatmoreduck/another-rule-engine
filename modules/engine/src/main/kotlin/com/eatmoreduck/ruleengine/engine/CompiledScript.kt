package com.eatmoreduck.ruleengine.engine

import groovy.lang.Binding
import groovy.lang.GroovyClassLoader
import groovy.lang.Script

/** Binding 中特征变量的固定键名（生产调用方约定，见 [CompiledScript.evaluateArgumentOf]） */
private const val FEATURES_BINDING_KEY = "features"

/**
 * 已编译的 Groovy 脚本（编译产物 + 所属隔离类加载器）。
 *
 * 每次调用 [run] 都会创建新的 Script 实例，因此本对象可被多线程安全地共享执行。
 * 类加载器随本条目由 [ScriptCompilationCache] 统一管理，条目被逐出时整体关闭丢弃。
 *
 * @property cacheKey 缓存键：SHA-256(沙箱配置版本 + 脚本文本)
 * @property scriptClass 编译产物脚本类（由独立 ClassLoader 加载）
 * @property classLoader 产生该脚本类的隔离类加载器
 */
class CompiledScript(
    val cacheKey: String,
    val scriptClass: Class<out Script>,
    val classLoader: GroovyClassLoader,
) {
    /**
     * 执行脚本（无超时控制；需要超时请走 [GroovyScriptEngine.execute]）。
     *
     * 兼容旧 GroovyScriptEngine 的两种脚本形态：
     * 1. 顶层表达式脚本：直接返回脚本最后一行的值
     * 2. `def evaluate(Map features) { ... }` 方法定义脚本：顶层 run() 返回 null 时，
     *    回退调用 evaluate(变量Map)
     *
     * @param variables 注入脚本的变量（如特征数据）
     * @return 脚本执行结果
     * @throws ScriptExecutionException 脚本运行失败
     */
    fun run(variables: Map<String, Any?> = emptyMap()): Any? {
        val scriptInstance = createScriptInstance()
        scriptInstance.binding = Binding(variables)
        val result = scriptInstance.run()
        return result ?: invokeEvaluateMethod(scriptInstance, evaluateArgumentOf(variables))
    }

    /**
     * evaluate(Map) 回退的实参约定。
     *
     * 生产调用方统一以 `mapOf("features" to features)` 包装注入（Binding 中变量名固定
     * 为 "features"，顶层表达式脚本同样经该键取特征），回退调用须剥掉包装层、把裸特征
     * Map 交给 evaluate——否则 evaluate 内取 `features.<字段>` 实为外层包装键，
     * 恒为 null（Groovy 中 `null < n` 为 true，会导致条件恒命中）。
     * 无包装层时（旧式裸 Map 调用）原样传递，两种风格兼容。
     */
    private fun evaluateArgumentOf(variables: Map<String, Any?>): Map<String, Any?> {
        val inner = variables[FEATURES_BINDING_KEY]
        @Suppress("UNCHECKED_CAST")
        return if (inner is Map<*, *>) inner as Map<String, Any?> else variables
    }

    /** 实例化脚本类 */
    private fun createScriptInstance(): Script =
        try {
            scriptClass.getDeclaredConstructor().newInstance() as Script
        } catch (e: ReflectiveOperationException) {
            throw ScriptExecutionException("脚本实例化失败: ${e.message}", e)
        }

    /** run() 返回 null 时，回退调用脚本的 evaluate(Map) 方法（若定义了该方法） */
    private fun invokeEvaluateMethod(
        scriptInstance: Script,
        variables: Map<String, Any?>,
    ): Any? =
        try {
            val evaluateMethod = scriptClass.getMethod("evaluate", Map::class.java)
            evaluateMethod.invoke(scriptInstance, variables)
        } catch (_: NoSuchMethodException) {
            // 脚本未定义 evaluate 方法，保持结果为 null
            null
        }
}
