package com.example.ruleengine.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * 批量测试请求
 * TEST-02: 支持多组数据批量测试
 */
public class BatchTestRequest {

    @NotBlank(message = "规则Key不能为空")
    private String ruleKey;

    @NotEmpty(message = "测试数据集不能为空")
    private List<Map<String, Object>> testDataList;

    private List<String> requiredFeatures;

    public BatchTestRequest() {}

    public String getRuleKey() {
        return ruleKey;
    }

    public void setRuleKey(String ruleKey) {
        this.ruleKey = ruleKey;
    }

    public List<Map<String, Object>> getTestDataList() {
        return testDataList;
    }

    public void setTestDataList(List<Map<String, Object>> testDataList) {
        this.testDataList = testDataList;
    }

    public List<String> getRequiredFeatures() {
        return requiredFeatures;
    }

    public void setRequiredFeatures(List<String> requiredFeatures) {
        this.requiredFeatures = requiredFeatures;
    }
}
