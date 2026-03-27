package com.example.ruleengine.model;

import java.util.Map;

/**
 * 特征响应模型
 */
public class FeatureResponse {

    private Map<String, Object> features;
    private boolean cacheHit;
    private boolean fallbackToDefault;
    private long fetchTimeMs;

    // 构造器
    public FeatureResponse() {}

    public FeatureResponse(Map<String, Object> features) {
        this.features = features;
    }

    // Getters and Setters
    public Map<String, Object> getFeatures() {
        return features;
    }

    public void setFeatures(Map<String, Object> features) {
        this.features = features;
    }

    public boolean isCacheHit() {
        return cacheHit;
    }

    public void setCacheHit(boolean cacheHit) {
        this.cacheHit = cacheHit;
    }

    public boolean isFallbackToDefault() {
        return fallbackToDefault;
    }

    public void setFallbackToDefault(boolean fallbackToDefault) {
        this.fallbackToDefault = fallbackToDefault;
    }

    public long getFetchTimeMs() {
        return fetchTimeMs;
    }

    public void setFetchTimeMs(long fetchTimeMs) {
        this.fetchTimeMs = fetchTimeMs;
    }
}
