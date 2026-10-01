package com.eatmoreduck.ruleengine.admin.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.eatmoreduck.ruleengine.admin.analytics.RuleConflictDetector
import com.eatmoreduck.ruleengine.admin.dto.ConflictResultResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 规则冲突检测 REST API 控制器（路径/响应字段与旧 ConflictDetectionController 一致：
 * /api/v1/conflicts 族，类级登录校验，无方法级权限码）。
 */
@RestController
@RequestMapping("/api/v1/conflicts")
@SaCheckLogin
class ConflictDetectionController(
    private val conflictDetector: RuleConflictDetector,
) {
    private val log = LoggerFactory.getLogger(ConflictDetectionController::class.java)

    /** 全量冲突检测：POST /api/v1/conflicts/detect */
    @PostMapping("/detect")
    fun detectAllConflicts(): ResponseEntity<List<ConflictResultResponse>> {
        log.info("执行全量冲突检测")
        return ResponseEntity.ok(conflictDetector.detectAllConflicts())
    }

    /** 单规则冲突检测：GET /api/v1/conflicts/rule/{ruleKey} */
    @GetMapping("/rule/{ruleKey}")
    fun detectConflictsForRule(
        @PathVariable ruleKey: String,
    ): ResponseEntity<List<ConflictResultResponse>> {
        log.info("执行单规则冲突检测: ruleKey={}", ruleKey)
        return ResponseEntity.ok(conflictDetector.detectConflictsForRule(ruleKey))
    }
}
