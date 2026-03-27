package com.example.ruleengine.exception;

public class RuleExecutionException extends RuntimeException {

    public RuleExecutionException(String message) {
        super(message);
    }

    public RuleExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
