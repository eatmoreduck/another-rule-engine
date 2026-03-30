package com.example.ruleengine.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 保存个人模板请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCustomTemplateRequest {

    @NotBlank(message = "模板名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "Groovy模板脚本不能为空")
    private String groovyTemplate;

    /**
     * 模板参数定义 (JSON 格式)
     */
    private String parameters;

    /**
     * 创建人
     */
    private String createdBy;
}
