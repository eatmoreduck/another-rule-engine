package com.example.ruleengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 决策表验证响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionTableValidateResponse {

    /**
     * 是否有效
     */
    private boolean valid;

    /**
     * 验证错误列表
     */
    private List<String> errors;

    /**
     * 验证警告列表
     */
    private List<String> warnings;

    public static DecisionTableValidateResponse valid() {
        return DecisionTableValidateResponse.builder()
                .valid(true)
                .errors(List.of())
                .warnings(List.of())
                .build();
    }

    public static DecisionTableValidateResponse invalid(List<String> errors) {
        return DecisionTableValidateResponse.builder()
                .valid(false)
                .errors(errors)
                .warnings(List.of())
                .build();
    }

    public static DecisionTableValidateResponse withWarnings(List<String> warnings) {
        return DecisionTableValidateResponse.builder()
                .valid(true)
                .errors(List.of())
                .warnings(warnings)
                .build();
    }
}
