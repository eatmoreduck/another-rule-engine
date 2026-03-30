package com.example.ruleengine.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 从模板实例化规则请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstantiateTemplateRequest {

    /**
     * 新规则的 ruleKey
     */
    @NotBlank(message = "规则Key不能为空")
    private String ruleKey;

    /**
     * 新规则名称（可选，默认使用模板名称）
     */
    private String ruleName;

    /**
     * 新规则描述（可选）
     */
    private String ruleDescription;

    /**
     * 模板参数值映射
     * key: 参数名, value: 参数值
     */
    private Map<String, Object> parameters;

    /**
     * 操作人
     */
    private String operator;
}
