package com.example.ruleengine.model.dto;

import com.example.ruleengine.domain.ExecutionLog;

import java.time.LocalDateTime;

/**
 * 执行日志响应 DTO
 * 映射前端期望的字段名：outputDecision->result, status->level, createdAt->executedAt
 */
public class ExecutionLogResponse {

    private Long id;
    private String ruleKey;
    private String ruleName;
    /** 执行结果：HIT / MISS / ERROR */
    private String result;
    /** 执行耗时（毫秒） */
    private Integer executionTime;
    /** 日志级别：INFO / WARN / ERROR */
    private String level;
    /** 错误信息 */
    private String errorMessage;
    /** 执行时间戳 */
    private LocalDateTime executedAt;

    public ExecutionLogResponse() {}

    /**
     * 从 ExecutionLog 实体转换为响应 DTO
     */
    public static ExecutionLogResponse fromEntity(ExecutionLog log) {
        ExecutionLogResponse response = new ExecutionLogResponse();
        response.id = log.getId();
        response.ruleKey = log.getRuleKey();
        response.ruleName = log.getRuleKey(); // 实体无 ruleName 字段，用 ruleKey 代替
        response.executionTime = log.getExecutionTimeMs();
        response.errorMessage = log.getErrorMessage();
        response.executedAt = log.getCreatedAt();

        // 映射 result: PASS->HIT, 其他->MISS 或 ERROR
        String decision = log.getOutputDecision();
        if ("PASS".equalsIgnoreCase(decision)) {
            response.result = "HIT";
        } else if ("ERROR".equals(log.getStatus())) {
            response.result = "ERROR";
        } else {
            response.result = "MISS";
        }

        // 映射 level: status->level
        switch (log.getStatus()) {
            case "SUCCESS":
                response.level = "INFO";
                break;
            case "TIMEOUT":
                response.level = "WARN";
                break;
            case "ERROR":
                response.level = "ERROR";
                break;
            default:
                response.level = "INFO";
        }

        return response;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRuleKey() {
        return ruleKey;
    }

    public void setRuleKey(String ruleKey) {
        this.ruleKey = ruleKey;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Integer getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(Integer executionTime) {
        this.executionTime = executionTime;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }
}
