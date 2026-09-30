package com.example.ruleengine.model.dto;

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
public class FeatureValidationResponse {

    private boolean valid;

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @Builder.Default
    private List<String> unknownFields = new ArrayList<>();

    @Builder.Default
    private List<ItemResult> items = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemResult {
        private String fieldName;
        private boolean found;
        private boolean matchedByAlias;
        private String canonicalCode;
        private String matchedAlias;
        private String dataType;
        private String sourceType;
        private String sensitivity;

        @Builder.Default
        private List<String> warnings = new ArrayList<>();
    }
}
