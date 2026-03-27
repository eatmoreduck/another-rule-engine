package com.example.ruleengine.engine;

import com.example.ruleengine.exception.RuleExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GroovyScriptEngine 单元测试
 */
@DisplayName("GroovyScriptEngine 测试")
class GroovyScriptEngineTest {

    private GroovyScriptEngine engine;
    private ScriptCacheManager cacheManager;
    private SecurityConfiguration securityConfig;
    private ClassLoaderManager classLoaderManager;

    @BeforeEach
    void setUp() {
        cacheManager = new ScriptCacheManager();
        securityConfig = new SecurityConfiguration();
        classLoaderManager = new ClassLoaderManager();
        engine = new GroovyScriptEngine(cacheManager, securityConfig, classLoaderManager);
    }

    @Test
    @DisplayName("应成功执行简单的 Groovy 脚本")
    void shouldExecuteSimpleScript() {
        String script = "def result = x + y; return result";
        Map<String, Object> context = Map.of("x", 10, "y", 20);

        Object result = engine.executeScript("test-rule", script, context);

        assertEquals(30, result);
    }

    @Test
    @DisplayName("应成功执行返回布尔值的脚本")
    void shouldExecuteBooleanScript() {
        String script = "return amount > 1000";
        Map<String, Object> context = Map.of("amount", 500);

        Object result = engine.executeScript("boolean-rule", script, context);

        assertFalse((Boolean) result);
    }

    @Test
    @DisplayName("应成功执行返回 Map 的脚本")
    void shouldExecuteMapScript() {
        String script = "return ['decision': 'PASS', 'reason': '金额正常']";
        Map<String, Object> context = Map.of();

        Object result = engine.executeScript("map-rule", script, context);

        assertInstanceOf(Map.class, result);
        Map<?, ?> resultMap = (Map<?, ?>) result;
        assertEquals("PASS", resultMap.get("decision"));
        assertEquals("金额正常", resultMap.get("reason"));
    }

    @Test
    @DisplayName("应缓存已编译的脚本")
    void shouldCacheCompiledScripts() {
        String script = "return x * 2";
        Map<String, Object> context = Map.of("x", 5);

        // 第一次执行
        Object result1 = engine.executeScript("cache-rule", script, context);
        int cacheSize1 = cacheManager.getCacheSize();

        // 第二次执行（应该从缓存获取）
        Object result2 = engine.executeScript("cache-rule", script, context);
        int cacheSize2 = cacheManager.getCacheSize();

        assertEquals(result1, result2);
        assertEquals(10, result1);
        assertTrue(cacheSize2 >= cacheSize1);
    }

    @Test
    @DisplayName("脚本执行失败时应抛出 RuleExecutionException")
    void shouldThrowExceptionWhenScriptFails() {
        String script = "throw new RuntimeException('测试异常')";
        Map<String, Object> context = Map.of();

        assertThrows(RuleExecutionException.class, () -> {
            engine.executeScript("error-rule", script, context);
        });
    }

    @Test
    @DisplayName("脚本编译失败时应抛出 RuleExecutionException")
    void shouldThrowExceptionWhenScriptCompilationFails() {
        String invalidScript = "def invalid_method( {"; // 语法错误
        Map<String, Object> context = Map.of();

        assertThrows(RuleExecutionException.class, () -> {
            engine.executeScript("compile-error-rule", invalidScript, context);
        });
    }
}
