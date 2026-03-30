package com.example.ruleengine.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 安全审计服务
 * SEC-01: 脚本编译前的 AST 安全审计
 *
 * 功能：
 * 1. 检测危险模式（递归、死循环、资源消耗）
 * 2. 静态分析脚本中的安全风险
 * 3. 记录安全审计日志
 */
@Service
public class SecurityAuditService {

    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditService.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("SECURITY_AUDIT");

    // 脚本最大长度限制（防止超大脚本消耗资源）
    private static final int MAX_SCRIPT_LENGTH = 65536;

    // 最大嵌套深度（防止深度递归）
    private static final int MAX_NESTING_DEPTH = 10;

    // 危险模式：System 操作
    private static final Pattern SYSTEM_CALL_PATTERN = Pattern.compile(
        "\\bSystem\\s*\\."
    );

    // 危险模式：Runtime 操作
    private static final Pattern RUNTIME_CALL_PATTERN = Pattern.compile(
        "\\bRuntime\\s*\\.|getRuntime\\s*\\(\\s*\\)"
    );

    // 危险模式：进程创建
    private static final Pattern PROCESS_PATTERN = Pattern.compile(
        "\\bProcessBuilder\\b|\\.exec\\s*\\("
    );

    // 危险模式：文件操作
    private static final Pattern FILE_OPERATION_PATTERN = Pattern.compile(
        "\\bnew\\s+File\\s*\\(|\\bnew\\s+FileInputStream\\s*\\(|" +
        "\\bnew\\s+FileOutputStream\\s*\\(|\\bnew\\s+FileWriter\\s*\\(|" +
        "\\bnew\\s+FileReader\\s*\\(|\\bnew\\s+RandomAccessFile\\s*\\(|" +
        "\\.delete\\s*\\(\\s*\\)"
    );

    // 危险模式：网络操作
    private static final Pattern NETWORK_PATTERN = Pattern.compile(
        "\\bnew\\s+URL\\s*\\(|\\bnew\\s+Socket\\s*\\(|" +
        "\\bnew\\s+ServerSocket\\s*\\(|\\bHttpURLConnection\\b|" +
        "\\bnew\\s+InetAddress\\s*\\("
    );

    // 危险模式：反射操作
    private static final Pattern REFLECTION_PATTERN = Pattern.compile(
        "Class\\.forName\\s*\\(|\\.getClass\\s*\\(\\s*\\)|" +
        "\\.getDeclaredMethod\\s*\\(|\\.getDeclaredField\\s*\\(|" +
        "\\.getDeclaredConstructor\\s*\\(|\\.setAccessible\\s*\\(|" +
        "\\.invoke\\s*\\(|java\\.lang\\.reflect\\."
    );

    // 危险模式：线程操作
    private static final Pattern THREAD_PATTERN = Pattern.compile(
        "\\bnew\\s+Thread\\s*\\(|\\.start\\s*\\(\\s*\\)|" +
        "\\bThread\\.sleep\\s*\\(|\\bThread\\.currentThread\\s*\\("
    );

    // 危险模式：Class 加载
    private static final Pattern CLASSLOADER_PATTERN = Pattern.compile(
        "\\bClassLoader\\b|\\.loadClass\\s*\\(|\\.defineClass\\s*\\("
    );

    // 危险模式：Groovy 内部调用
    private static final Pattern GROOVY_INTERNAL_PATTERN = Pattern.compile(
        "\\bGroovyShell\\b|\\bGroovyClassLoader\\b|\\bCompilerConfiguration\\b|" +
        "\\bEvaluate\\s*\\("
    );

    // 危险模式：无限循环
    private static final Pattern INFINITE_LOOP_PATTERN = Pattern.compile(
        "\\bwhile\\s*\\(\\s*true\\s*\\)|\\bfor\\s*\\(\\s*;\\s*;\\s*\\)|" +
        "\\bwhile\\s*\\(\\s*1\\s*\\)"
    );

    /**
     * 审计结果
     */
    public static class AuditResult {
        private final boolean safe;
        private final List<String> warnings;
        private final List<String> errors;

        private AuditResult(boolean safe, List<String> warnings, List<String> errors) {
            this.safe = safe;
            this.warnings = warnings;
            this.errors = errors;
        }

        public static AuditResult safe(List<String> warnings) {
            return new AuditResult(true, warnings, List.of());
        }

        public static AuditResult unsafe(List<String> warnings, List<String> errors) {
            return new AuditResult(false, warnings, errors);
        }

        public boolean isSafe() {
            return safe;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    /**
     * 对脚本进行安全审计
     *
     * @param scriptId 脚本ID（用于日志记录）
     * @param script   脚本内容
     * @return 审计结果
     */
    public AuditResult auditScript(String scriptId, String script) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // 1. 检查脚本长度
        if (script == null || script.isEmpty()) {
            errors.add("脚本内容为空");
            auditLogger.warn("[SECURITY_AUDIT] scriptId={}, error=空脚本", scriptId);
            return AuditResult.unsafe(warnings, errors);
        }

        if (script.length() > MAX_SCRIPT_LENGTH) {
            errors.add(String.format("脚本长度超出限制: %d > %d", script.length(), MAX_SCRIPT_LENGTH));
            auditLogger.warn("[SECURITY_AUDIT] scriptId={}, error=脚本超长, length={}", scriptId, script.length());
            return AuditResult.unsafe(warnings, errors);
        }

        // 2. 检查系统操作
        checkPattern(scriptId, script, SYSTEM_CALL_PATTERN, "System 调用", errors);

        // 3. 检查 Runtime 操作
        checkPattern(scriptId, script, RUNTIME_CALL_PATTERN, "Runtime 调用", errors);

        // 4. 检查进程创建
        checkPattern(scriptId, script, PROCESS_PATTERN, "进程创建", errors);

        // 5. 检查文件操作
        checkPattern(scriptId, script, FILE_OPERATION_PATTERN, "文件操作", errors);

        // 6. 检查网络操作
        checkPattern(scriptId, script, NETWORK_PATTERN, "网络操作", errors);

        // 7. 检查反射操作
        checkPattern(scriptId, script, REFLECTION_PATTERN, "反射操作", errors);

        // 8. 检查线程操作
        checkPattern(scriptId, script, THREAD_PATTERN, "线程操作", errors);

        // 9. 检查 ClassLoader 操作
        checkPattern(scriptId, script, CLASSLOADER_PATTERN, "ClassLoader 操作", errors);

        // 10. 检查 Groovy 内部调用
        checkPattern(scriptId, script, GROOVY_INTERNAL_PATTERN, "Groovy 内部调用", errors);

        // 11. 检查无限循环（警告级别）
        if (INFINITE_LOOP_PATTERN.matcher(script).find()) {
            warnings.add("检测到可能的无限循环模式");
            auditLogger.info("[SECURITY_AUDIT] scriptId={}, warning=可能的无限循环", scriptId);
        }

        // 12. 检查嵌套深度
        checkNestingDepth(scriptId, script, warnings);

        // 记录审计结果
        if (errors.isEmpty()) {
            auditLogger.info("[SECURITY_AUDIT] scriptId={}, result=PASSED, warnings={}", scriptId, warnings.size());
            logger.debug("脚本安全审计通过: scriptId={}, warnings={}", scriptId, warnings.size());
            return AuditResult.safe(warnings);
        } else {
            auditLogger.warn("[SECURITY_AUDIT] scriptId={}, result=REJECTED, errors={}, warnings={}",
                scriptId, errors.size(), warnings.size());
            logger.warn("脚本安全审计未通过: scriptId={}, errors={}", scriptId, errors);
            return AuditResult.unsafe(warnings, errors);
        }
    }

    /**
     * 检查脚本中的危险模式
     */
    private void checkPattern(String scriptId, String script, Pattern pattern,
                              String description, List<String> errors) {
        if (pattern.matcher(script).find()) {
            String msg = String.format("检测到危险的%s模式", description);
            errors.add(msg);
            auditLogger.warn("[SECURITY_AUDIT] scriptId={}, error={}", scriptId, msg);
        }
    }

    /**
     * 检查嵌套深度（防止深度递归/嵌套导致栈溢出）
     */
    private void checkNestingDepth(String scriptId, String script, List<String> warnings) {
        int maxDepth = 0;
        int currentDepth = 0;

        for (char c : script.toCharArray()) {
            if (c == '{' || c == '(' || c == '[') {
                currentDepth++;
                maxDepth = Math.max(maxDepth, currentDepth);
            } else if (c == '}' || c == ')' || c == ']') {
                currentDepth = Math.max(0, currentDepth - 1);
            }
        }

        if (maxDepth > MAX_NESTING_DEPTH) {
            warnings.add(String.format("嵌套深度 %d 超过建议阈值 %d", maxDepth, MAX_NESTING_DEPTH));
            auditLogger.info("[SECURITY_AUDIT] scriptId={}, warning=嵌套深度过深, depth={}", scriptId, maxDepth);
        }
    }
}
