package com.example.ruleengine.model.dto;

import com.example.ruleengine.constants.DecisionFlowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 决策流查询条件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionFlowQuery {
    private DecisionFlowStatus status;
    private String createdBy;
    private Boolean enabled;
    private String keyword;
    private LocalDateTime createdAtStart;
    private LocalDateTime createdAtEnd;
    private LocalDateTime updatedAtStart;
    private LocalDateTime updatedAtEnd;
}
