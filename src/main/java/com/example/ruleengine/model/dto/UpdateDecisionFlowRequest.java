package com.example.ruleengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新决策流请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDecisionFlowRequest {
    private String flowName;
    private String flowDescription;
    private String flowGraph;
    private String changeReason;
}
