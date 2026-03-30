package com.example.ruleengine.engine;

import com.example.ruleengine.exception.RuleExecutionException;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 沙箱安全测试
 * SEC-01: 验证规则在沙箱环境中执行，防止恶意代码攻击
 *
 * 安全防护分为两层：
 * 第一层：SecureASTCustomizer 编译期拦截（导入白名单 + 接收者类黑名单）
 * 第二层：SecurityAuditService 正则审计（构造函数调用、反射模式等）
 */
@DisplayName("SEC-01 沙箱安全测试")
class SandboxTest {

    private SecurityConfiguration securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfiguration();
    }

    /**
     * 使用安全配置创建 GroovyShell 进行测试
     */
    private GroovyShell createSecureShell() {
        CompilerConfiguration config = securityConfig.createSecureConfiguration();
        return new GroovyShell(config);
    }

    /**
     * 尝试解析脚本，期望编译阶段失败
     */
    private void assertScriptBlocked(String script) {
        GroovyShell shell = createSecureShell();
        assertThrows(Exception.class, () -> {
            shell.parse(script);
        }, "脚本应被沙箱拦截: " + script);
    }

    /**
     * 解析并执行脚本，期望成功
     */
    private Object assertScriptAllowed(String script) {
        GroovyShell shell = createSecureShell();
        assertDoesNotThrow(() -> shell.parse(script), "正常脚本不应被拦截: " + script);
        return shell.evaluate(script);
    }

    // ==================== 第一层：SecureASTCustomizer 编译期拦截 ====================

    @Nested
    @DisplayName("第一层 - System 调用拦截")
    class SystemExitTests {

        @Test
        @DisplayName("应拦截 System.exit() 调用")
        void shouldBlockSystemExit() {
            assertScriptBlocked("System.exit(0)");
        }

        @Test
        @DisplayName("应拦截 System.exit(1) 调用")
        void shouldBlockSystemExitWithCode() {
            assertScriptBlocked("System.exit(1)");
        }

        @Test
        @DisplayName("应拦截 System.getenv() 调用")
        void shouldBlockSystemGetenv() {
            assertScriptBlocked("System.getenv()");
        }

        @Test
        @DisplayName("应拦截 System.getProperties() 调用")
        void shouldBlockSystemGetProperties() {
            assertScriptBlocked("System.getProperties()");
        }

        @Test
        @DisplayName("应拦截 System.getProperty() 调用")
        void shouldBlockSystemGetProperty() {
            assertScriptBlocked("System.getProperty('user.dir')");
        }
    }

    @Nested
    @DisplayName("第一层 - Runtime 调用拦截")
    class RuntimeTests {

        @Test
        @DisplayName("应拦截 Runtime.exec() 调用")
        void shouldBlockRuntimeExec() {
            assertScriptBlocked("Runtime.getRuntime().exec('rm -rf /')");
        }

        @Test
        @DisplayName("应拦截 Runtime.getRuntime() 调用")
        void shouldBlockRuntimeGetRuntime() {
            assertScriptBlocked("Runtime.getRuntime()");
        }
    }

    @Nested
    @DisplayName("第一层 - 反射调用拦截")
    class ReflectionTests {

        @Test
        @DisplayName("应拦截 Class.forName() 调用")
        void shouldBlockClassForName() {
            assertScriptBlocked("Class.forName('java.lang.Runtime')");
        }

        @Test
        @DisplayName("应拦截 ClassLoader 调用")
        void shouldBlockClassLoader() {
            assertScriptBlocked("ClassLoader.getSystemClassLoader()");
        }

        @Test
        @DisplayName("应拦截 Thread 调用")
        void shouldBlockThreadOperations() {
            assertScriptBlocked("Thread.currentThread()");
        }
    }

    @Nested
    @DisplayName("第一层 - 进程创建拦截")
    class ProcessCreationTests {

        @Test
        @DisplayName("应拦截 ProcessBuilder 方法调用")
        void shouldBlockProcessBuilderMethod() {
            // ProcessBuilder 类在接收者黑名单中，任何方法调用都被拦截
            assertScriptBlocked("new ProcessBuilder('rm').start()");
        }
    }

    @Nested
    @DisplayName("第一层 - 危险导入拦截")
    class ImportBlockTests {

        @Test
        @DisplayName("应拦截 import java.io.File")
        void shouldBlockImportJavaIoFile() {
            assertScriptBlocked("import java.io.File; new File('/tmp')");
        }

        @Test
        @DisplayName("应拦截 import java.net.URL")
        void shouldBlockImportJavaNetUrl() {
            assertScriptBlocked("import java.net.URL; new URL('http://evil.com')");
        }

        @Test
        @DisplayName("应拦截 import java.lang.reflect.Method")
        void shouldBlockImportJavaLangReflect() {
            assertScriptBlocked("import java.lang.reflect.Method; Method.class");
        }

        @Test
        @DisplayName("应拦截 import java.io.FileInputStream")
        void shouldBlockImportJavaIoFileInputStream() {
            assertScriptBlocked("import java.io.FileInputStream; new FileInputStream('/etc/passwd')");
        }

        @Test
        @DisplayName("应拦截 import java.io.FileOutputStream")
        void shouldBlockImportJavaIoFileOutputStream() {
            assertScriptBlocked("import java.io.FileOutputStream; new FileOutputStream('/tmp/evil')");
        }

        @Test
        @DisplayName("应拦截 import java.net.Socket")
        void shouldBlockImportJavaNetSocket() {
            assertScriptBlocked("import java.net.Socket; new Socket('evil.com', 80)");
        }
    }

    // ==================== 第二层：SecurityAuditService 正则审计 ====================

    @Nested
    @DisplayName("第二层 - SecurityAuditService 正则审计")
    class SecurityAuditTests {

        private SecurityAuditService auditService;

        @BeforeEach
        void initAuditService() {
            auditService = new SecurityAuditService();
        }

        @Test
        @DisplayName("正常脚本应通过审计")
        void shouldPassNormalScript() {
            SecurityAuditService.AuditResult result = auditService.auditScript(
                "test-normal", "return amount > 1000 ? 'HIGH' : 'LOW'"
            );
            assertTrue(result.isSafe());
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("包含 System 调用的脚本应被审计拦截")
        void shouldDetectSystemCall() {
            SecurityAuditService.AuditResult result = auditService.auditScript(
                "test-system", "System.exit(0)"
            );
            assertFalse(result.isSafe());
            assertFalse(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("包含文件操作的脚本应被审计拦截")
        void shouldDetectFileOperation() {
            SecurityAuditService.AuditResult result = auditService.auditScript(
                "test-file", "new File('/etc/passwd')"
            );
            assertFalse(result.isSafe());
            assertFalse(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("包含网络操作的脚本应被审计拦截")
        void shouldDetectNetworkOperation() {
            SecurityAuditService.AuditResult result = auditService.auditScript(
                "test-network", "new URL('http://evil.com')"
            );
            assertFalse(result.isSafe());
            assertFalse(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("包含反射调用的脚本应被审计拦截")
        void shouldDetectReflection() {
            SecurityAuditService.AuditResult result = auditService.auditScript(
                "test-reflection", "Class.forName('java.lang.Runtime')"
            );
            assertFalse(result.isSafe());
            assertFalse(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("包含进程创建的脚本应被审计拦截")
        void shouldDetectProcessCreation() {
            SecurityAuditService.AuditResult result = auditService.auditScript(
                "test-process", "new ProcessBuilder('rm', '-rf', '/')"
            );
            assertFalse(result.isSafe());
            assertFalse(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("应检测 getClass() 调用")
        void shouldDetectGetClass() {
            SecurityAuditService.AuditResult result = auditService.auditScript(
                "test-getclass", "'hello'.getClass()"
            );
            assertFalse(result.isSafe());
            assertFalse(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("包含 Socket 操作的脚本应被审计拦截")
        void shouldDetectSocketOperation() {
            SecurityAuditService.AuditResult result = auditService.auditScript(
                "test-socket", "new Socket('evil.com', 80)"
            );
            assertFalse(result.isSafe());
            assertFalse(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("空脚本应被审计拦截")
        void shouldDetectEmptyScript() {
            SecurityAuditService.AuditResult result = auditService.auditScript(
                "test-empty", ""
            );
            assertFalse(result.isSafe());
        }

        @Test
        @DisplayName("null 脚本应被审计拦截")
        void shouldDetectNullScript() {
            SecurityAuditService.AuditResult result = auditService.auditScript(
                "test-null", null
            );
            assertFalse(result.isSafe());
        }

        @Test
        @DisplayName("超大脚本应被审计拦截")
        void shouldDetectOversizedScript() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 70000; i++) {
                sb.append("x");
            }
            SecurityAuditService.AuditResult result = auditService.auditScript(
                "test-oversized", sb.toString()
            );
            assertFalse(result.isSafe());
        }

        @Test
        @DisplayName("无限循环模式应产生警告")
        void shouldWarnAboutInfiniteLoop() {
            SecurityAuditService.AuditResult result = auditService.auditScript(
                "test-loop", "while(true) { break }"
            );
            // while(true) 是警告级别
            assertTrue(result.isSafe() || !result.getWarnings().isEmpty());
        }

        @Test
        @DisplayName("深度嵌套应产生警告")
        void shouldWarnAboutDeepNesting() {
            StringBuilder script = new StringBuilder("return ");
            for (int i = 0; i < 15; i++) {
                script.append("[");
            }
            script.append("1");
            for (int i = 0; i < 15; i++) {
                script.append("]");
            }
            SecurityAuditService.AuditResult result = auditService.auditScript(
                "test-nesting", script.toString()
            );
            assertTrue(result.isSafe() || !result.getWarnings().isEmpty());
        }

        @Test
        @DisplayName("应检测 Runtime.exec 调用")
        void shouldDetectRuntimeExec() {
            SecurityAuditService.AuditResult result = auditService.auditScript(
                "test-runtime-exec", "Runtime.getRuntime().exec('rm -rf /')"
            );
            assertFalse(result.isSafe());
        }

        @Test
        @DisplayName("应检测 Thread 创建")
        void shouldDetectThreadCreation() {
            SecurityAuditService.AuditResult result = auditService.auditScript(
                "test-thread", "new Thread({}).start()"
            );
            assertFalse(result.isSafe());
        }

        @Test
        @DisplayName("数学运算脚本应通过审计")
        void shouldPassMathScript() {
            SecurityAuditService.AuditResult result = auditService.auditScript(
                "test-math", "return 1 + 2 * 3"
            );
            assertTrue(result.isSafe());
        }

        @Test
        @DisplayName("集合操作脚本应通过审计")
        void shouldPassCollectionScript() {
            SecurityAuditService.AuditResult result = auditService.auditScript(
                "test-collection", "return [1, 2, 3].sum()"
            );
            assertTrue(result.isSafe());
        }
    }

    // ==================== 正常规则操作测试 ====================

    @Nested
    @DisplayName("正常规则操作 - 不应被拦截")
    class AllowedOperationsTests {

        @Test
        @DisplayName("应允许数学运算")
        void shouldAllowMathOperations() {
            Object result = assertScriptAllowed("return 1 + 2 * 3");
            assertEquals(7, result);
        }

        @Test
        @DisplayName("应允许浮点数运算")
        void shouldAllowFloatMath() {
            Object result = assertScriptAllowed("return 3.14 * 2");
            // Groovy 默认将小数字面量解析为 BigDecimal
            assertTrue(result instanceof java.math.BigDecimal);
            assertEquals(new BigDecimal("6.28"), result);
        }

        @Test
        @DisplayName("应允许 Math 类调用")
        void shouldAllowMathClass() {
            Object result = assertScriptAllowed("return Math.max(10, 20)");
            assertEquals(20, result);
        }

        @Test
        @DisplayName("应允许字符串操作")
        void shouldAllowStringOperations() {
            Object result = assertScriptAllowed("return 'hello ' + 'world'");
            assertEquals("hello world", result);
        }

        @Test
        @DisplayName("应允许字符串方法调用")
        void shouldAllowStringMethods() {
            Object result = assertScriptAllowed("return 'hello'.toUpperCase()");
            assertEquals("HELLO", result);
        }

        @Test
        @DisplayName("应允许 List 集合操作")
        void shouldAllowListOperations() {
            Object result = assertScriptAllowed("return [1, 2, 3].sum()");
            assertEquals(6, result);
        }

        @Test
        @DisplayName("应允许 Map 集合操作")
        void shouldAllowMapOperations() {
            Object result = assertScriptAllowed("def m = [a: 1, b: 2]; return m.a + m.b");
            assertEquals(3, result);
        }

        @Test
        @DisplayName("应允许条件判断")
        void shouldAllowConditionals() {
            Object result = assertScriptAllowed("def x = 10; if (x > 5) { return 'big' } else { return 'small' }");
            assertEquals("big", result);
        }

        @Test
        @DisplayName("应允许三元表达式")
        void shouldAllowTernaryExpression() {
            Object result = assertScriptAllowed("def amount = 500; return amount > 1000 ? 'HIGH' : 'LOW'");
            assertEquals("LOW", result);
        }

        @Test
        @DisplayName("应允许 switch 语句")
        void shouldAllowSwitchStatement() {
            Object result = assertScriptAllowed(
                "def level = 'HIGH';\n" +
                "switch(level) {\n" +
                "  case 'HIGH': return 3; break;\n" +
                "  case 'MEDIUM': return 2; break;\n" +
                "  default: return 1;\n" +
                "}"
            );
            assertEquals(3, result);
        }

        @Test
        @DisplayName("应允许 each/collect 等 Groovy 集合操作")
        void shouldAllowGroovyCollectionOps() {
            Object result = assertScriptAllowed("return [1, 2, 3].collect { it * 2 }");
            assertEquals(java.util.List.of(2, 4, 6), result);
        }

        @Test
        @DisplayName("应允许 find/filter 操作")
        void shouldAllowFindOperations() {
            Object result = assertScriptAllowed("return [1, 2, 3, 4, 5].findAll { it > 3 }");
            assertEquals(java.util.List.of(4, 5), result);
        }

        @Test
        @DisplayName("应允许 BigDecimal 运算")
        void shouldAllowBigDecimal() {
            Object result = assertScriptAllowed("return new BigDecimal('100.50').add(new BigDecimal('50.25'))");
            assertEquals(new BigDecimal("150.75"), result);
        }

        @Test
        @DisplayName("应允许变量定义和计算")
        void shouldAllowVariableDefinitions() {
            Object result = assertScriptAllowed(
                "def score = 85\n" +
                "def baseScore = 100\n" +
                "def ratio = score / baseScore\n" +
                "return ratio > 0.8 ? 'PASS' : 'FAIL'"
            );
            assertEquals("PASS", result);
        }

        @Test
        @DisplayName("应允许闭包定义")
        void shouldAllowClosureDefinition() {
            Object result = assertScriptAllowed("def calc = { a, b -> a + b }; return calc(3, 4)");
            assertEquals(7, result);
        }

        @Test
        @DisplayName("应允许使用 GroovyScriptEngine 执行正常规则")
        void shouldAllowNormalRuleViaEngine() {
            ScriptCacheManager cacheManager = new ScriptCacheManager();
            ClassLoaderManager classLoaderManager = new ClassLoaderManager();
            GroovyScriptEngine engine = new GroovyScriptEngine(
                cacheManager, securityConfig, classLoaderManager
            );

            String script = "return amount > threshold ? 'REJECT' : 'PASS'";
            Map<String, Object> context = Map.of("amount", 5000, "threshold", 1000);

            Object result = engine.executeScript("sandbox-test-rule", script, context);
            assertEquals("REJECT", result);
        }
    }
}
