package com.example.ruleengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 决策表转换响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionTableResponse {

    /**
     * 转换是否成功
     */
    private boolean success;

    /**
     * 生成的 Groovy DSL 脚本
     */
    private String groovyScript;

    /**
     * 错误信息（转换失败时）
     */
    private String errorMessage;

    /**
     * 规则行数
     */
    private int rowCount;

    public static DecisionTableResponse success(String groovyScript, int rowCount) {
        return DecisionTableResponse.builder()
                .success(true)
                .groovyScript(groovyScript)
                .rowCount(rowCount)
                .build();
    }

    public static DecisionTableResponse error(String errorMessage) {
        return DecisionTableResponse.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
