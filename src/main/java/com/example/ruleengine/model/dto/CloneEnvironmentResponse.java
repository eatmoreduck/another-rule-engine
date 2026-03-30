package com.example.ruleengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 环境克隆响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloneEnvironmentResponse {

    /**
     * 克隆是否成功
     */
    private boolean success;

    /**
     * 克隆的规则数量
     */
    private int clonedCount;

    /**
     * 跳过的规则数量（目标已存在且未选择覆盖）
     */
    private int skippedCount;

    /**
     * 消息
     */
    private String message;

    public static CloneEnvironmentResponse success(int clonedCount, int skippedCount) {
        return CloneEnvironmentResponse.builder()
                .success(true)
                .clonedCount(clonedCount)
                .skippedCount(skippedCount)
                .message(String.format("克隆完成: 复制 %d 条规则, 跳过 %d 条", clonedCount, skippedCount))
                .build();
    }
}
