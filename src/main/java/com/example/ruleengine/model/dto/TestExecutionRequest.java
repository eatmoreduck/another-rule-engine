package com.example.ruleengine.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * 规则测试执行请求
 * TEST-01: 支持模拟数据测试规则
 */
public class TestExecutionRequest {

    @NotBlank(message = "规则Key不能为空")
    private String ruleKey;

    @NotNull(message = "测试数据不能为空")
    private Map<String, Object> testData;

    private List<String> requiredFeatures;

    public TestExecutionRequest() {}

    public TestExecutionRequest(String ruleKey, Map<String, Object> testData) {
        this.ruleKey = ruleKey;
        this.testData = testData;
    }

    public String getRuleKey() {
        return ruleKey;
    }

    public void setRuleKey(String ruleKey) {
        this.ruleKey = ruleKey;
    }

    public Map<String, Object> getTestData() {
        return testData;
    }

    public void setTestData(Map<String, Object> testData) {
        this.testData = testData;
    }

    public List<String> getRequiredFeatures() {
        return requiredFeatures;
    }

    public void setRequiredFeatures(List<String> requiredFeatures) {
        this.requiredFeatures = requiredFeatures;
    }
}
