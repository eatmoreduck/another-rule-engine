package com.example.ruleengine.engine

/**
 * 静态审计结果。
 *
 * @property safe 是否通过审计（errors 为空即安全）
 * @property warnings 警告列表（不阻止执行，如疑似无限循环、嵌套过深）
 * @property errors 错误列表（任一存在即拒绝脚本）
 */
data class AuditResult(
    val safe: Boolean,
    val warnings: List<String>,
    val errors: List<String>,
) {
    companion object {
        fun safe(warnings: List<String>): AuditResult = AuditResult(true, warnings, emptyList())

        fun unsafe(
            warnings: List<String>,
            errors: List<String>,
        ): AuditResult = AuditResult(false, warnings, errors)
    }
}

/**
 * 脚本静态审计（第二层防护：编译前的文本预检）。
 *
 * 完整移植旧 SecurityAuditService：
 * 1. 脚本长度 / 空脚本检查
 * 2. 归一化脚本（消除常见正则绕过手段）后逐条匹配 [SandboxWhitelist.DANGEROUS_PATTERNS]
 * 3. 无限循环、嵌套深度产生警告（由执行期超时中断兜底）
 *
 * 归一化处理：
 * - 解析 Unicode 转义（S → S，防拆字绕过）
 * - 移除单行 / 多行注释（以空串替换，被注释拆分的 API 名重新连续，防拆分绕过）
 * - 合并相邻字符串字面量拼接（"Sys"+"tem" → System，防拆分 API 名绕过）
 * - 压缩连续空白（Sys tem.exit → System.exit）
 */
class ScriptAuditor(
    private val maxScriptLength: Int = SandboxWhitelist.MAX_SCRIPT_LENGTH,
    private val maxNestingDepth: Int = SandboxWhitelist.MAX_NESTING_DEPTH,
) {
    private val auditLogger = System.getLogger("SECURITY_AUDIT")

    /** 审计脚本，返回结果（不抛异常） */
    fun audit(script: String?): AuditResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. 空脚本检查
        if (script.isNullOrEmpty()) {
            errors.add("脚本内容为空")
            auditLogger.log(System.Logger.Level.WARNING, "[SECURITY_AUDIT] error=空脚本")
            return AuditResult.unsafe(warnings, errors)
        }

        // 2. 脚本长度检查
        if (script.length > maxScriptLength) {
            errors.add("脚本长度超出限制: ${script.length} > $maxScriptLength")
            auditLogger.log(System.Logger.Level.WARNING, "[SECURITY_AUDIT] error=脚本超长, length=${script.length}")
            return AuditResult.unsafe(warnings, errors)
        }

        // 3. 归一化后逐条匹配危险模式
        val normalized = normalize(script)
        SandboxWhitelist.DANGEROUS_PATTERNS.forEach { dangerousPattern ->
            if (dangerousPattern.pattern.matcher(normalized).find()) {
                errors.add("检测到危险的${dangerousPattern.name}模式：${dangerousPattern.reason}")
                auditLogger.log(
                    System.Logger.Level.WARNING,
                    "[SECURITY_AUDIT] error=检测到危险的${dangerousPattern.name}模式",
                )
            }
        }

        // 4. 无限循环模式（警告级别）
        if (SandboxWhitelist.INFINITE_LOOP_PATTERN.matcher(normalized).find()) {
            warnings.add("检测到可能的无限循环模式")
            auditLogger.log(System.Logger.Level.INFO, "[SECURITY_AUDIT] warning=可能的无限循环")
        }

        // 5. 嵌套深度检查（警告级别）
        val depth = maxNestingDepthOf(script)
        if (depth > maxNestingDepth) {
            warnings.add("嵌套深度 $depth 超过建议阈值 $maxNestingDepth")
            auditLogger.log(System.Logger.Level.INFO, "[SECURITY_AUDIT] warning=嵌套深度过深, depth=$depth")
        }

        return if (errors.isEmpty()) {
            AuditResult.safe(warnings)
        } else {
            auditLogger.log(System.Logger.Level.WARNING, "[SECURITY_AUDIT] result=REJECTED, errors=${errors.size}")
            AuditResult.unsafe(warnings, errors)
        }
    }

    /**
     * 归一化脚本文本，消除常见的正则绕过手段。
     * 步骤：Unicode 转义还原 → 移除注释 → 合并字符串拼接 → 压缩空白。
     */
    internal fun normalize(script: String): String {
        // 1. 解析 Unicode 转义序列（\uXXXX → 字符；仅可打印 ASCII 范围，其余保留原样）
        var result =
            UNICODE_ESCAPE.replace(script) { match ->
                val codePoint = match.groupValues[1].toIntOrNull(16)
                if (codePoint != null && codePoint in MIN_PRINTABLE_ASCII..MAX_PRINTABLE_ASCII) {
                    codePoint.toChar().toString()
                } else {
                    match.value
                }
            }

        // 2. 移除单行注释（// ... 至行尾；以空串替换，令被注释拆分的 API 名重新连续，防拆分绕过）
        result = result.replace(SINGLE_LINE_COMMENT, "")

        // 3. 移除多行注释（/* ... */，含跨行；同样以空串替换防拆分绕过，比旧实现的单行限制更严格）
        result = result.replace(MULTI_LINE_COMMENT, "")

        // 4. 合并相邻字符串字面量拼接，重复直到无法再合并（"Sys"+"tem.exit()" → System.exit()）
        var previous: String
        do {
            previous = result
            result =
                STRING_CONCAT.replace(result) { match ->
                    val doubleQuoted = match.groups[1]?.value to match.groups[2]?.value
                    val singleQuoted = match.groups[3]?.value to match.groups[4]?.value
                    when {
                        doubleQuoted.first != null && doubleQuoted.second != null -> {
                            doubleQuoted.first + doubleQuoted.second
                        }

                        singleQuoted.first != null && singleQuoted.second != null -> {
                            singleQuoted.first + singleQuoted.second
                        }

                        else -> {
                            match.value
                        }
                    }
                }
        } while (result != previous)

        // 5. 归一化空白：连续空白压缩为单个空格
        result = result.replace(WHITESPACE, " ")

        return result.trim()
    }

    /** 统计大括号/圆括号/方括号的最大嵌套深度（防止深度递归导致栈溢出） */
    private fun maxNestingDepthOf(script: String): Int {
        var maxDepth = 0
        var currentDepth = 0
        script.forEach { c ->
            when (c) {
                '{', '(', '[' -> {
                    currentDepth++
                    if (currentDepth > maxDepth) maxDepth = currentDepth
                }

                '}', ')', ']' -> {
                    currentDepth = maxOf(0, currentDepth - 1)
                }
            }
        }
        return maxDepth
    }

    companion object {
        private const val MIN_PRINTABLE_ASCII = 0x20
        private const val MAX_PRINTABLE_ASCII = 0x7E

        /** Unicode 转义序列 \uXXXX */
        private val UNICODE_ESCAPE: Regex = Regex("\\\\u([0-9a-fA-F]{4})")

        /** 单行注释 */
        private val SINGLE_LINE_COMMENT: Regex = Regex("//[^\\n]*")

        /** 多行注释（含跨行） */
        private val MULTI_LINE_COMMENT: Regex = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)

        /** 相邻字符串字面量拼接："abc" + "def" 或 'abc' + 'def' */
        private val STRING_CONCAT: Regex =
            Regex(
                "\"([^\"]*)\"\\s*\\+\\s*\"([^\"]*)\"|'([^']*)'\\s*\\+\\s*'([^']*)'",
            )

        /** 连续空白 */
        private val WHITESPACE: Regex = Regex("\\s+")
    }
}
