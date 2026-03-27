package com.example.ruleengine.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;

/**
 * 决策请求模型
 * Source: CONTEXT.md 决策 D-17
 */
public class DecisionRequest {

    @NotBlank(message = "规则ID不能为空")
    private String ruleId;

    @NotNull
    private String script;

    private Map<String, Object> features;

    private List<String> requiredFeatures;

    @Positive(message = "超时时间必须为正数")
    private long timeoutMs = 50;  // D-11: 默认 50ms 超时

    // 构造器
    public DecisionRequest() {}

    public DecisionRequest(String ruleId, String script, Map<String, Object> features) {
        this.ruleId = ruleId;
        this.script = script;
        this.features = features;
    }

    // Getters and Setters
    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public Map<String, Object> getFeatures() {
        return features;
    }

    public void setFeatures(Map<String, Object> features) {
        this.features = features;
    }

    public List<String> getRequiredFeatures() {
        return requiredFeatures;
    }

    public void setRequiredFeatures(List<String> requiredFeatures) {
        this.requiredFeatures = requiredFeatures;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
