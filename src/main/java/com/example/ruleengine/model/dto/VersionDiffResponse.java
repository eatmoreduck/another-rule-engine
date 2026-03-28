package com.example.ruleengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 版本差异响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionDiffResponse {

    private String ruleKey;
    private Integer version1;
    private Integer version2;
    private String script1;
    private String script2;
    private String diff;
}
