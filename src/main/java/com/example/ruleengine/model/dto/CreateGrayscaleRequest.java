package com.example.ruleengine.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建灰度发布配置请求
 * 支持规则（RULE）和决策流（DECISION_FLOW）灰度
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGrayscaleRequest {

    /**
     * 规则 Key（向后兼容，当 targetType=RULE 时使用）
     * 如果提供了 targetKey，则以 targetKey 为准
     */
    private String ruleKey;

    /**
     * 目标类型：RULE 或 DECISION_FLOW
     * 默认 RULE（向后兼容）
     */
    @Builder.Default
    private String targetType = "RULE";

    /**
     * 目标 Key（规则 Key 或决策流 Key）
     * 优先使用此字段，如果为空则使用 ruleKey
     */
    private String targetKey;

    @NotNull(message = "灰度版本号不能为空")
    private Integer grayscaleVersion;

    @NotNull(message = "灰度百分比不能为空")
    @Min(value = 0, message = "灰度百分比最小为0")
    @Max(value = 100, message = "灰度百分比最大为100")
    private Integer grayscalePercentage;

    /**
     * 灰度策略类型：PERCENTAGE / FEATURE / WHITELIST
     * 默认 PERCENTAGE
     */
    @Builder.Default
    private String strategyType = "PERCENTAGE";

    /**
     * 特征匹配规则（JSON 格式）
     * 当 strategyType=FEATURE 时必填
     * 格式: [{"field":"region","operator":"EQ","value":"US"}]
     */
    private String featureRules;

    /**
     * 白名单用户ID（逗号分隔）
     * 当 strategyType=WHITELIST 时必填
     */
    private String whitelistIds;

    /**
     * 是否启用双跑对比
     * 默认 false
     */
    @Builder.Default
    private Boolean dualRunEnabled = false;

    /**
     * 灰度描述
     */
    private String description;
}
