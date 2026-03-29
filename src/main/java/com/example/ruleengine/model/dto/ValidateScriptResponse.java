package com.example.ruleengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 脚本验证响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateScriptResponse {

    private boolean valid;
    private String errorMessage;
    private String errorDetails;

    public static ValidateScriptResponse success() {
        return ValidateScriptResponse.builder()
                .valid(true)
                .build();
    }

    public static ValidateScriptResponse error(String errorMessage, String errorDetails) {
        return ValidateScriptResponse.builder()
                .valid(false)
                .errorMessage(errorMessage)
                .errorDetails(errorDetails)
                .build();
    }
}
