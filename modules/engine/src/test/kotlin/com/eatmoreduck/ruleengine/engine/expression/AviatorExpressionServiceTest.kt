package com.eatmoreduck.ruleengine.engine.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Aviator 表达式求值服务测试：公式求值、编译缓存、变量提取、金额精度、错误收敛。
 */
class AviatorExpressionServiceTest {
    private val service = AviatorExpressionService()

    @Test
    fun `内置函数直接可用`() {
        val value = service.evaluate("max(amount * 2, 100) + abs(delta)", mapOf("amount" to 400, "delta" to -30))
        assertEquals(830.0, (value as Number).toDouble(), 0.0001)
    }

    @Test
    fun `浮点字面量按 BigDecimal 运算`() {
        // 0.1 + 0.2 在二进制浮点下是 0.30000000000000004；BigDecimal 模式应精确
        val value = service.evaluate("0.1 + 0.2", emptyMap())
        assertEquals("0.3", value.toString())
    }

    @Test
    fun `同公式重复求值结果一致(编译缓存)`() {
        val first = service.evaluate("amount * 2 + 100", mapOf("amount" to 400))
        val second = service.evaluate("amount * 2 + 100", mapOf("amount" to 400))
        assertEquals(first, second)
        assertEquals(900.0, (first as Number).toDouble(), 0.0001)
    }

    @Test
    fun `变量名提取用于依赖校验`() {
        val variables = service.variables("amount * 2 + riskFactor - otherCount")
        assertTrue("amount" in variables)
        assertTrue("riskFactor" in variables)
        assertTrue("otherCount" in variables)
    }

    @Test
    fun `引用缺失变量时报错收敛为 ExpressionEvaluationException`() {
        assertFailsWith<ExpressionEvaluationException> {
            service.evaluate("missingVar * 2", emptyMap())
        }
    }

    @Test
    fun `语法非法报错收敛为 ExpressionEvaluationException`() {
        assertFailsWith<ExpressionEvaluationException> {
            service.variables("amount >=")
        }
    }

    @Test
    fun `默认禁止任意方法调用(安全模型)`() {
        // 公式配置方不应能触达 System.exit 等危险能力
        assertFailsWith<ExpressionEvaluationException> {
            service.evaluate("System.exit(0)", emptyMap())
        }
    }
}

