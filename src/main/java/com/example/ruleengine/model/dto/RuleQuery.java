package com.example.ruleengine.model.dto;

import com.example.ruleengine.constants.RuleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 规则查询对象
 * 支持按状态、创建人、时间范围过滤和关键词搜索
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleQuery {

    /**
     * 规则状态过滤
     */
    private RuleStatus status;

    /**
     * 创建人过滤
     */
    private String createdBy;

    /**
     * 是否启用过滤
     */
    private Boolean enabled;

    /**
     * 关键词搜索（rule_key, rule_name）
     */
    private String keyword;

    /**
     * 创建时间范围-开始
     */
    private LocalDateTime createdAtStart;

    /**
     * 创建时间范围-结束
     */
    private LocalDateTime createdAtEnd;

    /**
     * 更新时间范围-开始
     */
    private LocalDateTime updatedAtStart;

    /**
     * 更新时间范围-结束
     */
    private LocalDateTime updatedAtEnd;
}
