package com.eatmoreduck.ruleengine.engine

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 引擎门面行为测试（移植旧 GroovyScriptEngineTest，并补充超时中断与 evaluate(Map) 模式用例）。
 */
class GroovyScriptEngineTest {
    @Test
    fun `simple script executes with injected variables`() {
        // 应成功执行简单的 Groovy 脚本（变量注入）
        GroovyScriptEngine().use { engine ->
            val result = engine.execute("def result = x + y; return result", mapOf("x" to 10, "y" to 20))
            assertEquals(30, result)
        }
    }

    @Test
    fun `boolean script executes`() {
        // 应成功执行返回布尔值的脚本
        GroovyScriptEngine().use { engine ->
            val result = engine.execute("return amount > 1000", mapOf("amount" to 500))
            assertFalse(result as Boolean)
        }
    }

    @Test
    fun `map script executes`() {
        // 应成功执行返回 Map 的脚本
        GroovyScriptEngine().use { engine ->
            val result = engine.execute("return ['decision': 'PASS', 'reason': '金额正常']")
            assertIs<Map<*, *>>(result)
            assertEquals("PASS", result["decision"])
            assertEquals("金额正常", result["reason"])
        }
    }

    @Test
    fun `null variable is visible inside script`() {
        // null 变量应可注入并在脚本内可见
        GroovyScriptEngine().use { engine ->
            val result = engine.execute("return riskScore == null", mapOf<String, Any?>("riskScore" to null))
            assertEquals(true, result)
        }
    }

    @Test
    fun `evaluate(Map) method script is supported`() {
        // 支持 def evaluate(Map features) { ... } 方法定义形态的脚本：
        // 顶层 run() 返回 null 时回退调用 evaluate(变量Map)
        GroovyScriptEngine().use { engine ->
            val script = "def evaluate(Map features) { return features.amount > 1000 }"
            assertEquals(true, engine.execute(script, mapOf("amount" to 5000)))
            assertEquals(false, engine.execute(script, mapOf("amount" to 100)))
        }
    }

    @Test
    fun `runtime exception is wrapped as ScriptExecutionException`() {
        // 脚本执行失败时应抛出 ScriptExecutionException
        GroovyScriptEngine().use { engine ->
            assertFailsWith<ScriptExecutionException> {
                engine.execute("throw new RuntimeException('测试异常')")
            }
        }
    }

    @Test
    fun `compilation failure is wrapped as ScriptCompilationException`() {
        // 脚本编译失败（语法错误）时应抛出 ScriptCompilationException
        GroovyScriptEngine().use { engine ->
            assertFailsWith<ScriptCompilationException> { engine.execute("def invalid_method( {") }
        }
    }

    @Test
    fun `empty script is rejected before compilation`() {
        // 空脚本应被静态审计直接拒绝
        GroovyScriptEngine().use { engine ->
            assertFailsWith<ScriptCompilationException> { engine.execute("") }
            assertFalse(engine.validate("").valid)
        }
    }

    // ==================== 执行超时 ====================

    @Test
    fun `infinite loop script is interrupted after timeout`() {
        // 死循环脚本应在超时后被中断：快速抛出 ScriptTimeoutException
        GroovyScriptEngine(defaultExecutionTimeout = Duration.ofMillis(400)).use { engine ->
            val startNanos = System.nanoTime()
            assertFailsWith<ScriptTimeoutException> {
                engine.execute("while(true) { }", timeout = Duration.ofMillis(400))
            }
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            assertTrue(elapsedMs < 10_000, "超时应在合理时间内返回，实际 ${elapsedMs}ms")
        }
    }

    @Test
    fun `infinite loop inside closure is interrupted after timeout`() {
        // 闭包内的死循环同样应被中断（闭包体注入中断检查点）
        GroovyScriptEngine(defaultExecutionTimeout = Duration.ofMillis(400)).use { engine ->
            assertFailsWith<ScriptTimeoutException> {
                engine.execute("[1].each { while (true) { } }", timeout = Duration.ofMillis(400))
            }
        }
    }

    @Test
    fun `fast script finishes well before timeout`() {
        // 正常脚本不应触发超时
        GroovyScriptEngine(defaultExecutionTimeout = Duration.ofSeconds(10)).use { engine ->
            assertEquals(42, engine.execute("return 42", timeout = Duration.ofSeconds(10)))
        }
    }

    @Test
    fun `interrupted loop stops executing`() {
        // 超时中断后循环应真正停止执行（循环体中断检查点生效），而非留下烧 CPU 的孤儿线程
        val counter =
            java.util.concurrent.atomic
                .AtomicLong()
        GroovyScriptEngine(defaultExecutionTimeout = Duration.ofMillis(300)).use { engine ->
            assertFailsWith<ScriptTimeoutException> {
                engine.execute("while (true) { counter.incrementAndGet() }", mapOf("counter" to counter))
            }
        }
        // 给中断传播留出一拍，然后采样增量：存活中的死循环每秒可迭代数千万次，中断后增量应接近 0
        Thread.sleep(200)
        val before = counter.get()
        Thread.sleep(300)
        val after = counter.get()
        assertTrue(after - before < 1_000_000, "中断后循环应停止执行: 增量=${after - before}")
    }
}
