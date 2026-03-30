package com.example.ruleengine.service.test;

import com.example.ruleengine.model.dto.TestResult;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试历史记录存储（内存存储）
 * TEST-02: 保存和查询测试历史
 *
 * 注意：此实现使用内存存储，重启后数据会丢失。
 * 后续可根据需要替换为数据库存储。
 */
@Component
public class TestHistoryStore {

    private static final int MAX_HISTORY_PER_RULE = 50;

    private final Map<String, List<TestResult>> historyMap = new ConcurrentHashMap<>();

    /**
     * 保存测试历史记录
     */
    public void saveTestHistory(String ruleKey, Object testData, TestResult result) {
        historyMap.computeIfAbsent(ruleKey, k -> new CopyOnWriteArrayList<>())
            .add(0, result);  // 最新记录在前

        // 限制每个规则最多保存的记录数
        List<TestResult> history = historyMap.get(ruleKey);
        if (history != null && history.size() > MAX_HISTORY_PER_RULE) {
            history.subList(MAX_HISTORY_PER_RULE, history.size()).clear();
        }
    }

    /**
     * 获取规则测试历史
     */
    public List<TestResult> getHistory(String ruleKey) {
        return historyMap.getOrDefault(ruleKey, Collections.emptyList());
    }
}
