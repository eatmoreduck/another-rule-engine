package com.example.ruleengine.model.dto;

/**
 * 规则冲突检测结果
 * TEST-03: 检测同一特征的冲突条件
 */
public class ConflictResult {

    private String conflictType;
    private String ruleKey1;
    private String ruleName1;
    private String ruleKey2;
    private String ruleName2;
    private String description;
    private String severity;

    public ConflictResult() {}

    public ConflictResult(String conflictType, String ruleKey1, String ruleName1,
                          String ruleKey2, String ruleName2, String description,
                          String severity) {
        this.conflictType = conflictType;
        this.ruleKey1 = ruleKey1;
        this.ruleName1 = ruleName1;
        this.ruleKey2 = ruleKey2;
        this.ruleName2 = ruleName2;
        this.description = description;
        this.severity = severity;
    }

    public String getConflictType() {
        return conflictType;
    }

    public void setConflictType(String conflictType) {
        this.conflictType = conflictType;
    }

    public String getRuleKey1() {
        return ruleKey1;
    }

    public void setRuleKey1(String ruleKey1) {
        this.ruleKey1 = ruleKey1;
    }

    public String getRuleName1() {
        return ruleName1;
    }

    public void setRuleName1(String ruleName1) {
        this.ruleName1 = ruleName1;
    }

    public String getRuleKey2() {
        return ruleKey2;
    }

    public void setRuleKey2(String ruleKey2) {
        this.ruleKey2 = ruleKey2;
    }

    public String getRuleName2() {
        return ruleName2;
    }

    public void setRuleName2(String ruleName2) {
        this.ruleName2 = ruleName2;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }
}
