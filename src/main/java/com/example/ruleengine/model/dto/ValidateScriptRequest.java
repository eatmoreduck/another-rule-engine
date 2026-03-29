package com.example.ruleengine.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 脚本验证请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateScriptRequest {

    @NotBlank(message = "Groovy脚本不能为空")
    private String groovyScript;
}
