package com.example.ruleengine.model.dto;

import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.domain.RuleVersion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 规则导出数据结构
 * 包含规则、版本历史
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleExportData {

    /**
     * 导出格式版本
     */
    @Builder.Default
    private String formatVersion = "1.0";

    /**
     * 导出时间戳
     */
    private String exportedAt;

    /**
     * 导出人
     */
    private String exportedBy;

    /**
     * 导出的规则列表
     */
    private List<RuleRecord> rules;

    /**
     * 单条规则导出记录
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleRecord {

        /**
         * 规则基本信息
         */
        private Rule rule;

        /**
         * 版本历史
         */
        private List<RuleVersion> versions;
    }
}
