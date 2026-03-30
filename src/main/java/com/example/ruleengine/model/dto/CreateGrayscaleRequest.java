package com.example.ruleengine.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建灰度发布配置请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGrayscaleRequest {

    @NotBlank(message = "规则Key不能为空")
    private String ruleKey;

    @NotNull(message = "灰度版本号不能为空")
    private Integer grayscaleVersion;

    @NotNull(message = "灰度百分比不能为空")
    @Min(value = 0, message = "灰度百分比最小为0")
    @Max(value = 100, message = "灰度百分比最大为100")
    private Integer grayscalePercentage;
}
