package com.example.ruleengine.validator;

import com.example.ruleengine.engine.SecurityAuditService;
import com.example.ruleengine.engine.SecurityConfiguration;
import lombok.Data;
import org.codehaus.groovy.control.CompilationFailedException;
import org.springframework.stereotype.Component;

import groovy.lang.GroovyShell;

/**
 * Groovy 脚本验证器
 *
 * 验证流程：
 * 1. 安全审计（SecurityAuditService） — 检测 System.exit()、Runtime.exec() 等危险模式
 * 2. 语法校验（沙箱 GroovyShell） — 使用 SecurityConfiguration 提供的沙箱配置编译脚本
 */
@Component
public class GroovyScriptValidator {

    private final SecurityAuditService securityAuditService;
    private final SecurityConfiguration securityConfiguration;

    public GroovyScriptValidator(SecurityAuditService securityAuditService,
                                 SecurityConfiguration securityConfiguration) {
        this.securityAuditService = securityAuditService;
        this.securityConfiguration = securityConfiguration;
    }

    /**
     * 验证 Groovy 脚本：先安全审计，再语法校验
     */
    public ValidationResult validate(String script) {
        // 第一步：安全审计，检测危险代码模式
        SecurityAuditService.AuditResult auditResult =
            securityAuditService.auditScript("validation", script);
        if (!auditResult.isSafe()) {
            String errorMsg = "安全审计失败: " + String.join("; ", auditResult.getErrors());
            return ValidationResult.error(errorMsg);
        }

        // 第二步：使用沙箱配置进行语法校验
        try {
            GroovyShell sandboxShell = new GroovyShell(
                securityConfiguration.createSecureConfiguration());
            sandboxShell.parse(script);
            return ValidationResult.success();
        } catch (CompilationFailedException e) {
            return ValidationResult.error(e.getMessage());
        }
    }

    /**
     * 验证结果
     */
    @Data
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult error(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
    }
}
