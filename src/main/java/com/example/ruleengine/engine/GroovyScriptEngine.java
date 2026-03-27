package com.example.ruleengine.engine;

import com.example.ruleengine.exception.RuleExecutionException;
import groovy.lang.Binding;
import groovy.lang.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Groovy 脚本执行引擎
 * Source: RESEARCH.md 模式1 + CONTEXT.md 决策 D-04
 *
 * 功能：
 * 1. 动态加载和执行 Groovy 脚本（D-04）
 * 2. 使用脚本缓存避免重复编译（PERF-01）
 * 3. 沙箱安全隔离（SEC-02）
 * 4. 类加载器生命周期管理
 */
@Component
public class GroovyScriptEngine {

    private static final Logger logger = LoggerFactory.getLogger(GroovyScriptEngine.class);

    private final ScriptCacheManager cacheManager;
    private final SecurityConfiguration securityConfig;
    private final ClassLoaderManager classLoaderManager;

    public GroovyScriptEngine(
        ScriptCacheManager cacheManager,
        SecurityConfiguration securityConfig,
        ClassLoaderManager classLoaderManager
    ) {
        this.cacheManager = cacheManager;
        this.securityConfig = securityConfig;
        this.classLoaderManager = classLoaderManager;
    }

    /**
     * 执行 Groovy 脚本
     * D-04: 使用 GroovyShell 动态编译和执行
     */
    public Object executeScript(String ruleId, String script, Map<String, Object> context) {
        try {
            // 计算脚本哈希值用于缓存键
            String scriptHash = calculateHash(script);

            // PERF-01: 从缓存获取已编译的脚本
            Class<?> scriptClass = cacheManager.get(ruleId, scriptHash);

            if (scriptClass == null) {
                // 缓存未命中，编译脚本
                scriptClass = compileScript(ruleId, script);
                cacheManager.put(ruleId, scriptHash, scriptClass);
                logger.debug("Compiled and cached script for rule: {}", ruleId);
            }

            // 执行脚本
            return executeScriptClass(scriptClass, context);

        } catch (Exception e) {
            logger.error("Failed to execute script for rule: {}", ruleId, e);
            throw new RuleExecutionException("脚本执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 编译 Groovy 脚本
     * D-04: 使用 GroovyShell 解析脚本
     */
    private Class<?> compileScript(String ruleId, String script) {
        try {
            groovy.lang.GroovyShell shell = new groovy.lang.GroovyShell(
                classLoaderManager.getClassLoader(ruleId),
                new Binding(),
                securityConfig.createSecureConfiguration()
            );

            return shell.parse(script).getClass();

        } catch (Exception e) {
            logger.error("Failed to compile script for rule: {}", ruleId, e);
            throw new RuleExecutionException("脚本编译失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行已编译的脚本类
     */
    private Object executeScriptClass(Class<?> scriptClass, Map<String, Object> context) {
        try {
            // 创建脚本实例
            Script scriptInstance = (Script) scriptClass.getDeclaredConstructor().newInstance();

            // 绑定上下文变量
            Binding binding = new Binding(context);
            scriptInstance.setBinding(binding);

            // 执行脚本
            return scriptInstance.run();

        } catch (Exception e) {
            logger.error("Failed to run script instance", e);
            throw new RuleExecutionException("脚本运行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 计算脚本哈希值（用于缓存键）
     */
    private String calculateHash(String script) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(script.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(script.hashCode());
        }
    }
}
