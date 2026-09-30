package com.example.ruleengine.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 静态审计专项测试：聚焦归一化（反绕过）能力与错误/警告信息质量。
 */
class ScriptAuditorTest {
    private val auditor = ScriptAuditor()

    // ==================== 归一化：绕过防御 ====================

    @Test
    fun `unicode escape obfuscation is decoded and detected`() {
        // S → S：拆字绕过应被还原并检出
        val obfuscated = "\\u0053ystem.exit(0)"
        assertFalse(auditor.audit(obfuscated).safe)
    }

    @Test
    fun `block comment obfuscation is stripped and detected`() {
        // 注释拆分 API 名应被检出：Sy/*x*/stem.exit → System.exit
        val obfuscated = "Sy/*x*/stem.exit(0)"
        assertFalse(auditor.audit(obfuscated).safe)
    }

    @Test
    fun `adjacent string literal split is merged and detected`() {
        // 字符串拼接拆分 API 名应被合并检出："Sys" + "tem.exit(0)"
        val obfuscated = "\"Sys\" + \"tem.exit(0)\""
        assertFalse(auditor.audit(obfuscated).safe)
    }

    @Test
    fun `whitespace obfuscation is collapsed and detected`() {
        // 空白拆分调用应被归一化检出：System . exit(0)
        val obfuscated = "System . exit(0)"
        assertFalse(auditor.audit(obfuscated).safe)
    }

    @Test
    fun `line comment does not hide payload on next line`() {
        // 单行注释后的危险代码仍应被检出
        val script =
            """
            // normal expression
            System.exit(0)
            """.trimIndent()
        assertFalse(auditor.audit(script).safe)
    }

    @Test
    fun `multi-line block comment payload is detected`() {
        // 跨行块注释包裹的危险代码应被检出
        val script =
            """
            /*
            System.exit(0)
            */
            return 1
            """.trimIndent()
        val result = auditor.audit(script)
        // 注释内容被移除后脚本本身安全，但含危险片段的多行注释同样被剥离，不误报
        assertTrue(result.safe)
    }

    // ==================== 长度与结构检查 ====================

    @Test
    fun `oversized script is rejected with limit message`() {
        // 超长脚本应被拒绝并说明限制
        val result = auditor.audit("x".repeat(70000))
        assertFalse(result.safe)
        assertTrue(result.errors.first().contains("65536"), "错误信息应包含长度上限: ${result.errors}")
    }

    @Test
    fun `deep nesting produces warning with depth info`() {
        // 深度嵌套应产生含深度值的警告
        val script = "return " + "(".repeat(15) + "1" + ")".repeat(15)
        val result = auditor.audit(script)
        assertTrue(result.safe)
        assertTrue(result.warnings.first().contains("嵌套深度"), "警告应说明嵌套深度: ${result.warnings}")
    }

    // ==================== 错误信息质量 ====================

    @Test
    fun `error message names the dangerous API and the reason`() {
        // 错误信息应指明哪个 API、为什么禁止
        val result = auditor.audit("new File('/tmp/x')")
        assertFalse(result.safe)
        val message = result.errors.first()
        assertTrue(message.contains("文件操作"), "应包含危险类别: $message")
        assertTrue(message.contains("禁止"), "应包含禁止原因: $message")
    }

    @Test
    fun `empty script error is explicit`() {
        // 空脚本错误信息应明确
        val result = auditor.audit("")
        assertEquals(listOf("脚本内容为空"), result.errors)
    }

    // ==================== 正常脚本不误报 ====================

    @Test
    fun `business rule expression passes audit without warnings`() {
        // 正常业务规则表达式应通过且无警告
        val script = "return amount > 10000 && country == 'CN' ? 'REVIEW' : 'PASS'"
        val result = auditor.audit(script)
        assertTrue(result.safe)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `collect closure passes audit`() {
        // 闭包集合操作不误报
        assertTrue(auditor.audit("return [1, 2, 3].collect { it * 2 }").safe)
    }

    @Test
    fun `normalize is idempotent`() {
        // 归一化应幂等
        val script = "\"Sys\" + \"tem . exit(0)\" // comment"
        assertEquals(auditor.normalize(script), auditor.normalize(auditor.normalize(script)))
    }
}
