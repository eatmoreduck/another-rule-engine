package com.example.ruleengine.validator;

import lombok.Data;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.SourceUnit;
import org.springframework.stereotype.Component;

import groovy.lang.GroovyShell;

/**
 * Groovy 脚本验证器
 */
@Component
public class GroovyScriptValidator {

    /**
     * 验证 Groovy 脚本语法
     */
    public ValidationResult validate(String script) {
        try {
            new GroovyShell().parse(script);
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
