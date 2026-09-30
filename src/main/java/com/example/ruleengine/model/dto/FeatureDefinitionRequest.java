package com.example.ruleengine.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureDefinitionRequest {

    @NotBlank(message = "特征编码不能为空")
    private String code;

    @NotBlank(message = "特征名称不能为空")
    private String name;

    @NotBlank(message = "特征类型不能为空")
    private String dataType;

    @NotBlank(message = "特征来源不能为空")
    private String sourceType;

    private String exampleValue;

    private String description;

    private String scope;

    @Builder.Default
    private String sensitivity = "NORMAL";

    @Builder.Default
    private String status = "ACTIVE";

    private String owner;

    @Builder.Default
    private List<String> aliases = new ArrayList<>();
}
