package com.example.ruleengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则引用关系DTO
 * 表示规则被哪个决策流/规则集引用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleReference {
    /**
     * 引用类型: decision_flow, rule_set
     */
    private String type;

    /**
     * 引用方ID
     */
    private Long id;

    /**
     * 引用方名称
     */
    private String name;

    /**
     * 引用方Key
     */
    private String key;
}
