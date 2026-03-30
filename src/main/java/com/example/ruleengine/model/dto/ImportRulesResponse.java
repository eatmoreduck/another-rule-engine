package com.example.ruleengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则导入响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportRulesResponse {

    /**
     * 导入是否成功
     */
    private boolean success;

    /**
     * 成功导入的规则数量
     */
    private int importedCount;

    /**
     * 跳过的规则数量（已存在）
     */
    private int skippedCount;

    /**
     * 失败的规则数量
     */
    private int failedCount;

    /**
     * 失败详情
     */
    private java.util.List<String> failures;

    /**
     * 消息
     */
    private String message;

    public static ImportRulesResponse success(int importedCount, int skippedCount, int failedCount, java.util.List<String> failures) {
        return ImportRulesResponse.builder()
                .success(true)
                .importedCount(importedCount)
                .skippedCount(skippedCount)
                .failedCount(failedCount)
                .failures(failures)
                .message(String.format("导入完成: 成功 %d, 跳过 %d, 失败 %d", importedCount, skippedCount, failedCount))
                .build();
    }
}
