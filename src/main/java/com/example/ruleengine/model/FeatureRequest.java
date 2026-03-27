package com.example.ruleengine.model;

import java.util.List;
import java.util.Map;

/**
 * 特征请求模型
 */
public class FeatureRequest {

    private Map<String, Object> inputFeatures;
    private List<String> requiredFeatures;
    private long timeoutMs = 20;  // 默认 20ms 超时

    // 构造器
    public FeatureRequest() {}

    public FeatureRequest(Map<String, Object> inputFeatures, List<String> requiredFeatures) {
        this.inputFeatures = inputFeatures;
        this.requiredFeatures = requiredFeatures;
    }

    // Getters and Setters
    public Map<String, Object> getInputFeatures() {
        return inputFeatures;
    }

    public void setInputFeatures(Map<String, Object> inputFeatures) {
        this.inputFeatures = inputFeatures;
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
