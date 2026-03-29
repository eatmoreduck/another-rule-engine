package com.example.ruleengine.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新规则请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRuleRequest {

    private String ruleName;

    private String ruleDescription;

    private String groovyScript;

    private String changeReason;
}
