package com.example.ruleengine.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建规则请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRuleRequest {

    @NotBlank(message = "规则Key不能为空")
    private String ruleKey;

    @NotBlank(message = "规则名称不能为空")
    private String ruleName;

    private String ruleDescription;

    @NotBlank(message = "Groovy脚本不能为空")
    private String groovyScript;
}
