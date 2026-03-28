package com.example.ruleengine.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建版本请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateVersionRequest {

    @NotBlank(message = "规则脚本不能为空")
    private String groovyScript;

    private String changeReason;
}
