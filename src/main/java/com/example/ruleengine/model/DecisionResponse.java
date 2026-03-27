package com.example.ruleengine.model;

import java.util.Map;

/**
 * 决策响应模型
 */
public class DecisionResponse {

    private String decision;  // "PASS" 或 "REJECT"
    private String reason;
    private long executionTimeMs;
    private boolean timeout;
    private Map<String, Object> executionContext;

    // 构造器
    public DecisionResponse() {}

    public DecisionResponse(String decision, String reason, long executionTimeMs) {
        this.decision = decision;
        this.reason = reason;
        this.executionTimeMs = executionTimeMs;
    }

    // Getters and Setters
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

    public boolean isTimeout() {
        return timeout;
    }

    public void setTimeout(boolean timeout) {
        this.timeout = timeout;
    }

    public Map<String, Object> getExecutionContext() {
        return executionContext;
    }

    public void setExecutionContext(Map<String, Object> executionContext) {
        this.executionContext = executionContext;
    }

    // Builder 模式
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private DecisionResponse response = new DecisionResponse();

        public Builder decision(String decision) {
            response.decision = decision;
            return this;
        }

        public Builder reason(String reason) {
            response.reason = reason;
            return this;
        }

        public Builder executionTimeMs(long executionTimeMs) {
            response.executionTimeMs = executionTimeMs;
            return this;
        }

        public Builder timeout(boolean timeout) {
            response.timeout = timeout;
            return this;
        }

        public DecisionResponse build() {
            return response;
        }
    }
}
