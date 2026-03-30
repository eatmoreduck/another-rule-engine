package com.example.ruleengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 灰度对比报告响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrayscaleReportResponse {

    private Long configId;
    private String ruleKey;
    private Integer currentVersion;
    private Integer grayscaleVersion;
    private Integer grayscalePercentage;

    /**
     * 当前版本指标
     */
    private VersionMetrics currentVersionMetrics;

    /**
     * 灰度版本指标
     */
    private VersionMetrics grayscaleVersionMetrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionMetrics {
        private Integer version;
        private Integer executionCount;
        private Integer hitCount;
        private Integer errorCount;
        private Integer avgExecutionTimeMs;
        private Double errorRate;
        private Double hitRate;
    }
}
