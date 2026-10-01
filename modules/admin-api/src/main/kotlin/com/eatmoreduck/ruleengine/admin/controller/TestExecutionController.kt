package com.eatmoreduck.ruleengine.admin.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.eatmoreduck.ruleengine.admin.dto.TestResult
import com.eatmoreduck.ruleengine.admin.testexecution.TestExecutionService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 规则测试执行 REST API 控制器（路径/方法/响应结构与旧 TestExecutionController 一致，
 * 供规则详情页 RuleTestModal 调用：POST /api/v1/test/rules/{ruleKey}/execute）。
 *
 * 鉴权口径与旧一致：类级登录校验，无方法级权限码。
 */
@RestController
@RequestMapping("/api/v1/test")
@SaCheckLogin
class TestExecutionController(
    private val testExecutionService: TestExecutionService,
) {
    private val log = LoggerFactory.getLogger(TestExecutionController::class.java)

    /** 用模拟数据测试规则：POST /api/v1/test/rules/{ruleKey}/execute（请求体为特征名 → 测试值 的 Map） */
    @PostMapping("/rules/{ruleKey}/execute")
    fun executeTest(
        @PathVariable ruleKey: String,
        @RequestBody testData: Map<String, Any?>,
    ): ResponseEntity<TestResult> {
        log.info("测试规则: ruleKey={}", ruleKey)
        return ResponseEntity.ok(testExecutionService.executeTest(ruleKey, testData))
    }
}
