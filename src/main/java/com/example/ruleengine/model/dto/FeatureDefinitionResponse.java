package com.example.ruleengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureDefinitionResponse {

    private Long id;
    private String code;
    private String name;
    private String dataType;
    private String sourceType;
    private String exampleValue;
    private String description;
    private String scope;
    private String sensitivity;
    private String status;
    private String owner;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<String> aliases = new ArrayList<>();
}
