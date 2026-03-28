package com.example.ruleengine.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回滚请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollbackRequest {

    @NotNull(message = "目标版本号不能为空")
    private Integer targetVersion;

    private String reason;
}
