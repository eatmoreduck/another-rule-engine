package com.example.ruleengine.engine

import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.codehaus.groovy.control.CompilerConfiguration

/**
 * Groovy 脚本编译与执行的最小封装（阶段 0 冒烟实现）。
 *
 * 阶段 1 将扩展为生产级实现：
 * - 编译结果缓存（Caffeine，避免每次执行重复编译）
 * - SecureASTCustomizer 沙箱白名单校验
 * - 独立 ClassLoader 隔离，防止脚本污染宿主
 * - CompilerConfiguration.PARALLEL_PARSE 并行解析
 */
class ScriptCompiler(
    private val configuration: CompilerConfiguration = CompilerConfiguration(),
) {
    /**
     * 编译并执行脚本，返回脚本最后一行的值。
     *
     * @param script Groovy 脚本文本
     * @param variables 脚本内可见的变量（如特征数据）
     */
    fun evaluate(
        script: String,
        variables: Map<String, Any?> = emptyMap(),
    ): Any? {
        val binding = Binding()
        variables.forEach { (name, value) -> binding.setVariable(name, value) }
        return GroovyShell(binding, configuration).evaluate(script)
    }
}
