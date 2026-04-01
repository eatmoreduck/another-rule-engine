package com.example.ruleengine.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建决策流请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDecisionFlowRequest {
    @NotBlank(message = "决策流Key不能为空")
    private String flowKey;

    @NotBlank(message = "决策流名称不能为空")
    private String flowName;

    private String flowDescription;

    @NotBlank(message = "流程图数据不能为空")
    private String flowGraph;
}
