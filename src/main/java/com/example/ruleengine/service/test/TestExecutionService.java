package com.example.ruleengine.service.test;

import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.engine.GroovyScriptEngine;
import com.example.ruleengine.model.dto.TestResult;
import com.example.ruleengine.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则测试执行服务
 * TEST-01, TEST-02: 支持使用模拟数据测试规则
 *
 * 功能：
 * 1. 使用 mock 特征数据执行规则（不实际调用特征服务）
 * 2. 返回测试决策结果、匹配条件、执行时间
 * 3. 支持批量测试多组数据
 * 4. 保存测试历史记录
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TestExecutionService {

    private final GroovyScriptEngine scriptEngine;
    private final RuleRepository ruleRepository;
    private final TestHistoryStore testHistoryStore;

    /**
     * 使用模拟数据测试规则
     * TEST-01: TestResult executeTest(ruleKey, testData)
     *
     * @param ruleKey  规则Key
     * @param testData 模拟特征数据
     * @return 测试结果
     */
    public TestResult executeTest(String ruleKey, Map<String, Object> testData) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 加载规则
            Optional<Rule> ruleOpt = ruleRepository.findByRuleKey(ruleKey);
            if (ruleOpt.isEmpty()) {
                return TestResult.failure(ruleKey,
                    "规则不存在: " + ruleKey,
                    System.currentTimeMillis() - startTime);
            }

            Rule rule = ruleOpt.get();

            // 2. 使用模拟数据直接执行脚本（不经过特征服务）
            Object result = scriptEngine.executeScript(
                ruleKey, rule.getGroovyScript(), testData);

            long executionTime = System.currentTimeMillis() - startTime;

            // 3. 解析结果
            TestResult testResult = parseTestResult(ruleKey, result, testData, executionTime);

            // 4. 保存测试历史
            testHistoryStore.saveTestHistory(ruleKey, testData, testResult);

            return testResult;

        } catch (Exception e) {
            log.error("测试规则执行失败: ruleKey={}", ruleKey, e);
            return TestResult.failure(ruleKey,
                "测试执行失败: " + e.getMessage(),
                System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 批量测试（多组数据）
     * TEST-02: 支持多组模拟数据批量执行
     *
     * @param ruleKey      规则Key
     * @param testDataList 模拟数据列表
     * @return 测试结果列表
     */
    public List<TestResult> executeBatchTest(String ruleKey,
                                              List<Map<String, Object>> testDataList) {
        List<TestResult> results = new ArrayList<>();

        for (Map<String, Object> testData : testDataList) {
            TestResult result = executeTest(ruleKey, testData);
            results.add(result);
        }

        log.info("批量测试完成: ruleKey={}, total={}, success={}",
            ruleKey, testDataList.size(),
            results.stream().filter(TestResult::isSuccess).count());

        return results;
    }

    /**
     * 获取规则测试历史
     * TEST-02: 返回历史测试记录
     *
     * @param ruleKey 规则Key
     * @return 测试历史列表
     */
    public List<TestResult> getTestHistory(String ruleKey) {
        return testHistoryStore.getHistory(ruleKey);
    }

    /**
     * 解析脚本执行结果为 TestResult
     */
    private TestResult parseTestResult(String ruleKey, Object result,
                                        Map<String, Object> testData,
                                        long executionTime) {
        String decision;
        String reason;
        List<String> matchedConditions = extractMatchedConditions(testData);

        if (result instanceof Boolean) {
            decision = ((Boolean) result) ? "PASS" : "REJECT";
            reason = "规则执行完成";
        } else if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            Object decisionObj = resultMap.get("decision");
            Object reasonObj = resultMap.get("reason");
            decision = decisionObj != null ? decisionObj.toString() : "REJECT";
            reason = reasonObj != null ? reasonObj.toString() : "规则执行完成";
        } else if (result instanceof String) {
            decision = (String) result;
            reason = "规则执行完成";
        } else {
            decision = "REJECT";
            reason = "规则返回无效结果";
        }

        Map<String, Object> context = new HashMap<>(testData);

        return TestResult.success(ruleKey, decision, reason,
            executionTime, matchedConditions, context);
    }

    /**
     * 从测试数据中提取匹配的条件描述
     */
    private List<String> extractMatchedConditions(Map<String, Object> testData) {
        List<String> conditions = new ArrayList<>();
        for (Map.Entry<String, Object> entry : testData.entrySet()) {
            conditions.add(entry.getKey() + " = " + entry.getValue());
        }
        return conditions;
    }
}
