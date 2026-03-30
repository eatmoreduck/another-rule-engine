package com.example.ruleengine.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 决策表转换请求
 * 输入: 行列表格式的条件+动作
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionTableRequest {

    /**
     * 条件列定义
     * key: 条件名称, value: 条件类型 (如 "STRING", "NUMBER", "BOOLEAN")
     */
    @NotEmpty(message = "条件列不能为空")
    private Map<String, String> conditionColumns;

    /**
     * 动作列定义
     * key: 动作名称, value: 动作类型
     */
    @NotEmpty(message = "动作列不能为空")
    private Map<String, String> actionColumns;

    /**
     * 决策规则行数据
     * 每行包含条件和对应的动作
     */
    @NotEmpty(message = "规则行不能为空")
    private List<DecisionRow> rows;

    /**
     * 规则名称（可选，用于生成的脚本标识）
     */
    private String ruleName;

    /**
     * 决策行数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DecisionRow {

        /**
         * 条件值映射
         * key: 条件名称, value: 条件值（支持 * 表示任意值）
         */
        @NotEmpty(message = "条件值不能为空")
        private Map<String, Object> conditions;

        /**
         * 动作值映射
         * key: 动作名称, value: 动作值
         */
        @NotEmpty(message = "动作值不能为空")
        private Map<String, Object> actions;
    }
}
