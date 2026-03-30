package com.example.ruleengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 环境克隆请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloneEnvironmentRequest {

    /**
     * 是否覆盖目标环境已有的同名规则
     */
    @Builder.Default
    private Boolean overwrite = false;

    /**
     * 操作人
     */
    private String operator;
}
