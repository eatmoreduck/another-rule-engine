package com.example.ruleengine.model.dto;

import java.util.List;
import java.util.Map;

/**
 * 规则测试执行结果
 * TEST-01: 返回测试决策结果
 */
public class TestResult {

    private String ruleKey;
    private String decision;
    private String reason;
    private long executionTimeMs;
    private boolean success;
    private String errorMessage;
    private List<String> matchedConditions;
    private Map<String, Object> executionContext;

    public TestResult() {}

    public static TestResult success(String ruleKey, String decision, String reason,
                                     long executionTimeMs, List<String> matchedConditions,
                                     Map<String, Object> executionContext) {
        TestResult result = new TestResult();
        result.ruleKey = ruleKey;
        result.decision = decision;
        result.reason = reason;
        result.executionTimeMs = executionTimeMs;
        result.success = true;
        result.matchedConditions = matchedConditions;
        result.executionContext = executionContext;
        return result;
    }

    public static TestResult failure(String ruleKey, String errorMessage, long executionTimeMs) {
        TestResult result = new TestResult();
        result.ruleKey = ruleKey;
        result.success = false;
        result.errorMessage = errorMessage;
        result.executionTimeMs = executionTimeMs;
        return result;
    }

    public String getRuleKey() {
        return ruleKey;
    }

    public void setRuleKey(String ruleKey) {
        this.ruleKey = ruleKey;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<String> getMatchedConditions() {
        return matchedConditions;
    }

    public void setMatchedConditions(List<String> matchedConditions) {
        this.matchedConditions = matchedConditions;
    }

    public Map<String, Object> getExecutionContext() {
        return executionContext;
    }

    public void setExecutionContext(Map<String, Object> executionContext) {
        this.executionContext = executionContext;
    }
}
