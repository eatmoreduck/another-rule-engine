package com.example.ruleengine.model.dto;

import com.example.ruleengine.domain.GrayscaleConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 灰度发布配置响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrayscaleConfigResponse {

    private Long id;
    private String ruleKey;
    private Integer currentVersion;
    private Integer grayscaleVersion;
    private Integer grayscalePercentage;
    private String status;
    private String statusDescription;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String createdBy;
    private LocalDateTime createdAt;

    /**
     * 从实体转换为响应对象
     */
    public static GrayscaleConfigResponse fromEntity(GrayscaleConfig entity) {
        return GrayscaleConfigResponse.builder()
                .id(entity.getId())
                .ruleKey(entity.getRuleKey())
                .currentVersion(entity.getCurrentVersion())
                .grayscaleVersion(entity.getGrayscaleVersion())
                .grayscalePercentage(entity.getGrayscalePercentage())
                .status(entity.getStatus().name())
                .statusDescription(entity.getStatus().getDescription())
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
