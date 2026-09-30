package com.example.ruleengine.decision.controller

import cn.dev33.satoken.annotation.SaCheckPermission
import com.example.ruleengine.decision.async.AsyncDecisionService
import com.example.ruleengine.decision.dto.DecisionRequest
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 异步决策 API 控制器（REXEC-02，契约与旧 AsyncDecisionController 逐字对齐）：
 * - POST /api/v1/decide/async：提交异步决策，立即返回 202 {requestId, status: PROCESSING}；
 * - GET /api/v1/decide/async/{requestId}：轮询结果——完成返回
 *   {requestId, status: COMPLETED, decision, reason, executionTimeMs, timeout}，未完成返回 PROCESSING。
 */
@RestController
@RequestMapping("/api/v1/decide/async")
class AsyncDecisionController(
    private val asyncDecisionService: AsyncDecisionService,
) {
    private val log = LoggerFactory.getLogger(AsyncDecisionController::class.java)

    @PostMapping(
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    @SaCheckPermission("api:decision:execute")
    fun submitAsyncDecision(
        @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody request: DecisionRequest,
    ): ResponseEntity<Map<String, String>> {
        log.info("收到异步决策请求: ruleId={}", request.ruleId)
        val requestId =
            asyncDecisionService.submit(
                ruleId = request.ruleId,
                script = request.script,
                features = request.features,
                requiredFeatures = request.requiredFeatures,
                timeoutMs = request.timeoutMs,
            )
        return ResponseEntity.accepted().body(linkedMapOf("requestId" to requestId, "status" to "PROCESSING"))
    }

    @GetMapping(value = ["/{requestId}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @SaCheckPermission("api:decision:execute")
    fun getAsyncResult(
        @PathVariable requestId: String,
    ): ResponseEntity<Map<String, Any?>> {
        log.debug("查询异步决策结果: requestId={}", requestId)
        val outcome = asyncDecisionService.get(requestId)
        return if (outcome != null) {
            ResponseEntity.ok(
                linkedMapOf(
                    "requestId" to requestId,
                    "status" to "COMPLETED",
                    "decision" to outcome.action.name,
                    "reason" to outcome.reason,
                    "executionTimeMs" to outcome.executionTimeMs,
                    "timeout" to outcome.timedOut,
                ),
            )
        } else {
            ResponseEntity.ok(linkedMapOf("requestId" to requestId, "status" to "PROCESSING"))
        }
    }
}
