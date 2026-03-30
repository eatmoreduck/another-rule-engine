package com.example.ruleengine.controller;

import com.example.ruleengine.model.dto.BatchTestRequest;
import com.example.ruleengine.model.dto.TestExecutionRequest;
import com.example.ruleengine.model.dto.TestResult;
import com.example.ruleengine.service.test.TestExecutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 规则测试执行 REST API 控制器
 * TEST-01, TEST-02: 提供规则模拟测试接口
 */
@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
@Slf4j
public class TestExecutionController {

    private final TestExecutionService testExecutionService;

    /**
     * 用模拟数据测试规则
     * POST /api/v1/test/rules/{ruleKey}/execute
     */
    @PostMapping("/rules/{ruleKey}/execute")
    public ResponseEntity<TestResult> executeTest(
            @PathVariable String ruleKey,
            @Valid @RequestBody Map<String, Object> testData) {
        log.info("测试规则: ruleKey={}", ruleKey);
        TestResult result = testExecutionService.executeTest(ruleKey, testData);
        return ResponseEntity.ok(result);
    }

    /**
     * 批量测试（多组数据）
     * POST /api/v1/test/rules/{ruleKey}/batch
     */
    @PostMapping("/rules/{ruleKey}/batch")
    public ResponseEntity<List<TestResult>> executeBatchTest(
            @PathVariable String ruleKey,
            @Valid @RequestBody List<Map<String, Object>> testDataList) {
        log.info("批量测试规则: ruleKey={}, 数据组数={}", ruleKey, testDataList.size());
        List<TestResult> results = testExecutionService.executeBatchTest(ruleKey, testDataList);
        return ResponseEntity.ok(results);
    }

    /**
     * 获取测试历史
     * GET /api/v1/test/rules/{ruleKey}/history
     */
    @GetMapping("/rules/{ruleKey}/history")
    public ResponseEntity<List<TestResult>> getTestHistory(@PathVariable String ruleKey) {
        log.info("获取测试历史: ruleKey={}", ruleKey);
        List<TestResult> history = testExecutionService.getTestHistory(ruleKey);
        return ResponseEntity.ok(history);
    }
}
