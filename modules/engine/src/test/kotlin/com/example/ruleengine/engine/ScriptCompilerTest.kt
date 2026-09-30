package com.example.ruleengine.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 阶段 0 冒烟测试：验证 Groovy 5.1.3 在 JDK 25 上可正常编译并执行脚本。
 * 这是整个 Kotlin 重写最大的技术风险点，必须最先验证。
 */
class ScriptCompilerTest {
    private val compiler = ScriptCompiler()

    @Test
    fun `arithmetic script evaluates to integer result`() {
        val result = compiler.evaluate("1 + 1")
        assertEquals(2, result)
    }

    @Test
    fun `boolean expression evaluates with injected variables`() {
        val result = compiler.evaluate("amount > 1000 && userId != null", mapOf("amount" to 5000, "userId" to "u-1"))
        assertEquals(true, result)
    }

    @Test
    fun `null variable is visible inside script`() {
        val result = compiler.evaluate("riskScore == null", mapOf<String, Any?>("riskScore" to null))
        assertEquals(true, result)
    }
}
