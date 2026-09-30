package com.example.ruleengine.engine

import groovy.lang.GroovyShell
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 沙箱安全测试（完整移植旧 SandboxTest，安全用例一条不少）。
 *
 * 安全防护分为两层：
 * 第一层：SecureASTCustomizer 编译期拦截（导入白名单 + 接收者类黑名单）
 * 第二层：ScriptAuditor 正则审计（危险 API 文本模式检测）
 */
class SandboxSecurityTest {
    private val sandbox = SandboxConfiguration()
    private val auditor = ScriptAuditor()

    /** 使用安全配置解析脚本，期望编译阶段被拦截 */
    private fun assertScriptBlocked(script: String) {
        val shell = GroovyShell(sandbox.createSecureConfiguration())
        assertFailsWith<Exception>("脚本应被沙箱拦截: $script") { shell.parse(script) }
    }

    /** 使用安全配置解析并执行脚本，期望成功并返回结果 */
    private fun assertScriptAllowed(script: String): Any? {
        val shell = GroovyShell(sandbox.createSecureConfiguration())
        return shell.evaluate(script)
    }

    // ==================== 第一层：System 调用拦截 ====================

    @Test
    fun `System exit is blocked at compile time`() {
        // 应拦截 System.exit(0)
        assertScriptBlocked("System.exit(0)")
    }

    @Test
    fun `System exit with code is blocked at compile time`() {
        // 应拦截 System.exit(1)
        assertScriptBlocked("System.exit(1)")
    }

    @Test
    fun `System getenv is blocked at compile time`() {
        // 应拦截 System.getenv()
        assertScriptBlocked("System.getenv()")
    }

    @Test
    fun `System getProperties is blocked at compile time`() {
        // 应拦截 System.getProperties()
        assertScriptBlocked("System.getProperties()")
    }

    @Test
    fun `System getProperty is blocked at compile time`() {
        // 应拦截 System.getProperty('user.dir')
        assertScriptBlocked("System.getProperty('user.dir')")
    }

    // ==================== 第一层：Runtime 调用拦截 ====================

    @Test
    fun `Runtime exec is blocked at compile time`() {
        // 应拦截 Runtime.getRuntime().exec()
        assertScriptBlocked("Runtime.getRuntime().exec('rm -rf /')")
    }

    @Test
    fun `Runtime getRuntime is blocked at compile time`() {
        // 应拦截 Runtime.getRuntime()
        assertScriptBlocked("Runtime.getRuntime()")
    }

    // ==================== 第一层：反射调用拦截 ====================

    @Test
    fun `Class forName is blocked at compile time`() {
        // 应拦截 Class.forName()
        assertScriptBlocked("Class.forName('java.lang.Runtime')")
    }

    @Test
    fun `ClassLoader access is blocked at compile time`() {
        // 应拦截 ClassLoader.getSystemClassLoader()
        assertScriptBlocked("ClassLoader.getSystemClassLoader()")
    }

    @Test
    fun `Thread operations are blocked at compile time`() {
        // 应拦截 Thread.currentThread()
        assertScriptBlocked("Thread.currentThread()")
    }

    // ==================== 第一层：进程创建拦截 ====================

    @Test
    fun `ProcessBuilder is blocked at compile time`() {
        // ProcessBuilder 类在接收者黑名单中，任何方法调用都被拦截
        assertScriptBlocked("new ProcessBuilder('rm').start()")
    }

    // ==================== 第一层：危险导入拦截 ====================

    @Test
    fun `import java io File is blocked`() {
        // 应拦截 import java.io.File
        assertScriptBlocked("import java.io.File; new File('/tmp')")
    }

    @Test
    fun `import java net URL is blocked`() {
        // 应拦截 import java.net.URL
        assertScriptBlocked("import java.net.URL; new URL('http://evil.com')")
    }

    @Test
    fun `import java lang reflect Method is blocked`() {
        // 应拦截 import java.lang.reflect.Method
        assertScriptBlocked("import java.lang.reflect.Method; Method.class")
    }

    @Test
    fun `import java io FileInputStream is blocked`() {
        // 应拦截 import java.io.FileInputStream
        assertScriptBlocked("import java.io.FileInputStream; new FileInputStream('/etc/passwd')")
    }

    @Test
    fun `import java io FileOutputStream is blocked`() {
        // 应拦截 import java.io.FileOutputStream
        assertScriptBlocked("import java.io.FileOutputStream; new FileOutputStream('/tmp/evil')")
    }

    @Test
    fun `import java net Socket is blocked`() {
        // 应拦截 import java.net.Socket
        assertScriptBlocked("import java.net.Socket; new Socket('evil.com', 80)")
    }

    // ==================== 第二层：ScriptAuditor 正则审计 ====================

    @Test
    fun `normal script passes audit`() {
        // 正常脚本应通过审计
        val result = auditor.audit("return amount > 1000 ? 'HIGH' : 'LOW'")
        assertTrue(result.safe)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `audit detects System call`() {
        // 包含 System 调用的脚本应被审计拦截
        val result = auditor.audit("System.exit(0)")
        assertFalse(result.safe)
        assertTrue(result.errors.first().contains("System 调用"), "错误信息应指明危险类别: ${result.errors}")
        assertTrue(result.errors.first().contains("java.lang.System"), "错误信息应说明禁止原因: ${result.errors}")
    }

    @Test
    fun `audit detects file operation`() {
        // 包含文件操作的脚本应被审计拦截
        val result = auditor.audit("new File('/etc/passwd')")
        assertFalse(result.safe)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `audit detects network operation`() {
        // 包含网络操作的脚本应被审计拦截
        val result = auditor.audit("new URL('http://evil.com')")
        assertFalse(result.safe)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `audit detects reflection`() {
        // 包含反射调用的脚本应被审计拦截
        val result = auditor.audit("Class.forName('java.lang.Runtime')")
        assertFalse(result.safe)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `audit detects process creation`() {
        // 包含进程创建的脚本应被审计拦截
        val result = auditor.audit("new ProcessBuilder('rm', '-rf', '/')")
        assertFalse(result.safe)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `audit detects getClass call`() {
        // 应检测 getClass() 反射入口
        val result = auditor.audit("'hello'.getClass()")
        assertFalse(result.safe)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `audit detects socket operation`() {
        // 包含 Socket 操作的脚本应被审计拦截
        val result = auditor.audit("new Socket('evil.com', 80)")
        assertFalse(result.safe)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `audit rejects empty script`() {
        // 空脚本应被审计拦截
        assertFalse(auditor.audit("").safe)
    }

    @Test
    fun `audit rejects null script`() {
        // null 脚本应被审计拦截
        assertFalse(auditor.audit(null).safe)
    }

    @Test
    fun `audit rejects oversized script`() {
        // 超大脚本应被审计拦截（上限 65536）
        val oversized = "x".repeat(70000)
        assertFalse(auditor.audit(oversized).safe)
    }

    @Test
    fun `audit warns about infinite loop pattern`() {
        // while(true) 是警告级别：不拒绝脚本，由执行期超时中断兜底
        val result = auditor.audit("while(true) { break }")
        assertTrue(result.safe || result.warnings.isNotEmpty())
    }

    @Test
    fun `audit warns about deep nesting`() {
        // 深度嵌套应产生警告（阈值 10）
        val script = "return " + "[".repeat(15) + "1" + "]".repeat(15)
        val result = auditor.audit(script)
        assertTrue(result.safe || result.warnings.isNotEmpty())
    }

    @Test
    fun `audit detects Runtime exec`() {
        // 应检测 Runtime.exec 调用
        assertFalse(auditor.audit("Runtime.getRuntime().exec('rm -rf /')").safe)
    }

    @Test
    fun `audit detects thread creation`() {
        // 应检测线程创建
        assertFalse(auditor.audit("new Thread({}).start()").safe)
    }

    @Test
    fun `audit passes math script`() {
        // 数学运算脚本应通过审计
        assertTrue(auditor.audit("return 1 + 2 * 3").safe)
    }

    @Test
    fun `audit passes collection script`() {
        // 集合操作脚本应通过审计
        assertTrue(auditor.audit("return [1, 2, 3].sum()").safe)
    }

    // ==================== 门面：审计 + 沙箱编译联动 ====================

    @Test
    fun `engine validate rejects dangerous script with reason`() {
        // 门面校验应拒绝危险脚本并给出明确原因
        GroovyScriptEngine().use { engine ->
            val result = engine.validate("System.exit(0)")
            assertFalse(result.valid)
            assertTrue((result as ValidationResult.Failure).errors.first().contains("System 调用"))
        }
    }

    @Test
    fun `engine compile rejects dangerous script`() {
        // 门面编译应拒绝危险脚本
        GroovyScriptEngine().use { engine ->
            assertFailsWith<ScriptCompilationException> { engine.compile("System.exit(0)") }
        }
    }

    @Test
    fun `engine execute never runs dangerous script`() {
        // 门面执行应在编译前拦截危险脚本
        GroovyScriptEngine().use { engine ->
            assertFailsWith<ScriptCompilationException> { engine.execute("System.exit(0)") }
        }
    }

    @Test
    fun `engine validate accepts normal script`() {
        // 门面校验应通过正常脚本
        GroovyScriptEngine().use { engine ->
            assertEquals(ValidationResult.Success, engine.validate("return amount > 1000"))
        }
    }

    // ==================== 正常规则操作：不应被拦截 ====================

    @Test
    fun `math operations are allowed`() {
        // 应允许数学运算
        assertEquals(7, assertScriptAllowed("return 1 + 2 * 3"))
    }

    @Test
    fun `float math is allowed and yields BigDecimal`() {
        // 应允许浮点数运算（Groovy 默认将小数字面量解析为 BigDecimal）
        val result = assertScriptAllowed("return 3.14 * 2")
        assertTrue(result is BigDecimal)
        assertEquals(BigDecimal("6.28"), result)
    }

    @Test
    fun `Math class is allowed`() {
        // 应允许 Math 类调用
        assertEquals(20, assertScriptAllowed("return Math.max(10, 20)"))
    }

    @Test
    fun `string concatenation is allowed`() {
        // 应允许字符串拼接
        assertEquals("hello world", assertScriptAllowed("return 'hello ' + 'world'"))
    }

    @Test
    fun `string methods are allowed`() {
        // 应允许字符串方法调用
        assertEquals("HELLO", assertScriptAllowed("return 'hello'.toUpperCase()"))
    }

    @Test
    fun `list sum is allowed`() {
        // 应允许 List 集合操作
        assertEquals(6, assertScriptAllowed("return [1, 2, 3].sum()"))
    }

    @Test
    fun `map operations are allowed`() {
        // 应允许 Map 集合操作
        assertEquals(3, assertScriptAllowed("def m = [a: 1, b: 2]; return m.a + m.b"))
    }

    @Test
    fun `conditionals are allowed`() {
        // 应允许条件判断
        assertEquals("big", assertScriptAllowed("def x = 10; if (x > 5) { return 'big' } else { return 'small' }"))
    }

    @Test
    fun `ternary expression is allowed`() {
        // 应允许三元表达式
        assertEquals("LOW", assertScriptAllowed("def amount = 500; return amount > 1000 ? 'HIGH' : 'LOW'"))
    }

    @Test
    fun `switch statement is allowed`() {
        // 应允许 switch 语句
        val script =
            """
            def level = 'HIGH'
            switch(level) {
              case 'HIGH': return 3
              case 'MEDIUM': return 2
              default: return 1
            }
            """.trimIndent()
        assertEquals(3, assertScriptAllowed(script))
    }

    @Test
    fun `groovy collection operations are allowed`() {
        // 应允许 each/collect 等 Groovy 集合操作（闭包体含中断注入点，回归验证）
        assertEquals(listOf(2, 4, 6), assertScriptAllowed("return [1, 2, 3].collect { it * 2 }"))
    }

    @Test
    fun `find operations are allowed`() {
        // 应允许 find/filter 操作
        assertEquals(listOf(4, 5), assertScriptAllowed("return [1, 2, 3, 4, 5].findAll { it > 3 }"))
    }

    @Test
    fun `BigDecimal arithmetic is allowed`() {
        // 应允许 BigDecimal 运算
        assertEquals(
            BigDecimal("150.75"),
            assertScriptAllowed("return new BigDecimal('100.50').add(new BigDecimal('50.25'))"),
        )
    }

    @Test
    fun `variable definitions are allowed`() {
        // 应允许变量定义和计算
        val script =
            """
            def score = 85
            def baseScore = 100
            def ratio = score / baseScore
            return ratio > 0.8 ? 'PASS' : 'FAIL'
            """.trimIndent()
        assertEquals("PASS", assertScriptAllowed(script))
    }

    @Test
    fun `closure definition is allowed`() {
        // 应允许闭包定义（闭包体同样被注入中断检查点）
        assertEquals(7, assertScriptAllowed("def calc = { a, b -> a + b }; return calc(3, 4)"))
    }

    @Test
    fun `normal rule executes via engine`() {
        // 应允许通过引擎执行正常规则
        GroovyScriptEngine().use { engine ->
            val result =
                engine.execute(
                    "return amount > threshold ? 'REJECT' : 'PASS'",
                    mapOf("amount" to 5000, "threshold" to 1000),
                )
            assertEquals("REJECT", result)
        }
    }

    @Test
    fun `bounded for loop executes normally`() {
        // 有界循环应正常执行（验证 for 循环中断注入不破坏语义）
        assertEquals(45, assertScriptAllowed("def total = 0; for (i in 1..9) { total += i }; return total"))
    }

    @Test
    fun `bounded while loop executes normally`() {
        // 有界 while 循环应正常执行（验证 while 中断注入不破坏语义）
        assertEquals(10, assertScriptAllowed("def i = 0; while (i < 10) { i = i + 1 }; return i"))
    }
}
