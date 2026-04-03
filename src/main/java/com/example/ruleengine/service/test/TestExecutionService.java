package com.example.ruleengine.service.test;

import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.engine.GroovyScriptEngine;
import com.example.ruleengine.model.dto.TestResult;
import com.example.ruleengine.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 规则测试执行服务
 * 使用 mock 特征数据执行规则（不实际调用特征服务）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TestExecutionService {

    private final GroovyScriptEngine scriptEngine;
    private final RuleRepository ruleRepository;

    /**
     * 使用模拟数据测试规则
     */
    public TestResult executeTest(String ruleKey, Map<String, Object> testData) {
        long startTime = System.currentTimeMillis();

        try {
            Optional<Rule> ruleOpt = ruleRepository.findByRuleKey(ruleKey);
            if (ruleOpt.isEmpty()) {
                return TestResult.failure(ruleKey,
                    "规则不存在: " + ruleKey,
                    System.currentTimeMillis() - startTime);
            }

            Rule rule = ruleOpt.get();

            Object result = scriptEngine.executeScript(
                ruleKey, rule.getGroovyScript(), testData);

            long executionTime = System.currentTimeMillis() - startTime;

            return parseTestResult(ruleKey, result, testData, executionTime);

        } catch (Exception e) {
            log.error("测试规则执行失败: ruleKey={}", ruleKey, e);
            return TestResult.failure(ruleKey,
                "测试执行失败: " + e.getMessage(),
                System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 解析脚本执行结果为 TestResult
     */
    private TestResult parseTestResult(String ruleKey, Object result,
                                        Map<String, Object> testData,
                                        long executionTime) {
        String decision;
        String reason;
        List<String> matchedConditions = new ArrayList<>();
        for (Map.Entry<String, Object> entry : testData.entrySet()) {
            matchedConditions.add(entry.getKey() + " = " + entry.getValue());
        }

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
}
