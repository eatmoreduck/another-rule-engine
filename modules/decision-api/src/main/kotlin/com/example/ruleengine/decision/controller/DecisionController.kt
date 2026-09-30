package com.example.ruleengine.decision.controller

import cn.dev33.satoken.annotation.SaCheckPermission
import com.example.ruleengine.decision.core.DecisionService
import com.example.ruleengine.decision.dto.DecisionRequest
import com.example.ruleengine.decision.dto.DecisionResponse
import com.example.ruleengine.domain.DecisionResult
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
 * 决策 API 控制器（契约与旧 DecisionController 逐字对齐）：
 * - POST /api/v1/decide：直传脚本决策（D-16/D-17/D-18）；
 * - POST /api/v1/decide/{ruleKey}：cache-aware 决策（灰度分流 + 快照缓存，请求体仅为特征 Map）；
 * - POST /api/v1/decision-flows/{flowKey}/execute：决策流执行；
 * - GET /api/v1/health：健康检查（免认证白名单）。
 *
 * fail-safe：服务层几乎不抛异常（内部全捕获返回 REJECT）；防御性兜底返回
 * 500 + REJECT 响应体（旧契约行为，错误 JSON 结构不适用于本端点）。
 */
@RestController
@RequestMapping("/api/v1")
class DecisionController(
    private val decisionService: DecisionService,
) {
    private val log = LoggerFactory.getLogger(DecisionController::class.java)

    @PostMapping(
        value = ["/decide"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    @SaCheckPermission("api:decision:execute")
    suspend fun decide(
        @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody request: DecisionRequest,
    ): ResponseEntity<DecisionResponse> {
        log.info("Received decision request for rule: {}", request.ruleId)
        return try {
            val result =
                decisionService.executeAdHoc(
                    ruleId = request.ruleId,
                    script = request.script ?: "",
                    features = request.features,
                    requiredFeatures = request.requiredFeatures,
                    timeoutMs = request.timeoutMs,
                )
            ResponseEntity.ok(toResponse(result))
        } catch (e: Exception) {
            log.error("Decision request failed for rule: {}", request.ruleId, e)
            ResponseEntity
                .internalServerError()
                .body(errorResponse("决策请求失败: ${e.message}"))
        }
    }

    @PostMapping(
        value = ["/decide/{ruleKey}"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    @SaCheckPermission("api:decision:execute")
    suspend fun decideByKey(
        @PathVariable ruleKey: String,
        @org.springframework.web.bind.annotation.RequestBody features: Map<String, Any?>?,
    ): ResponseEntity<DecisionResponse> {
        log.info("Received cache-aware decision request for ruleKey: {}", ruleKey)
        return try {
            val result =
                decisionService.decideByKey(
                    ruleKey = ruleKey,
                    features = features,
                    requiredFeatures = null,
                    timeoutMs = DEFAULT_TIMEOUT_MS,
                )
            ResponseEntity.ok(toResponse(result))
        } catch (e: Exception) {
            log.error("Cache-aware decision request failed for ruleKey: {}", ruleKey, e)
            ResponseEntity
                .internalServerError()
                .body(errorResponse("决策请求失败: ${e.message}"))
        }
    }

    @PostMapping(
        value = ["/decision-flows/{flowKey}/execute"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    @SaCheckPermission("api:decision:execute")
    suspend fun decideByFlow(
        @PathVariable flowKey: String,
        @org.springframework.web.bind.annotation.RequestBody features: Map<String, Any?>?,
    ): ResponseEntity<DecisionResponse> {
        log.info("Received decision flow request for flowKey: {}", flowKey)
        return try {
            val result = decisionService.executeFlow(flowKey, features)
            ResponseEntity.ok(toResponse(result))
        } catch (e: Exception) {
            log.error("Decision flow request failed for flowKey: {}", flowKey, e)
            ResponseEntity
                .internalServerError()
                .body(errorResponse("决策流执行失败: ${e.message}"))
        }
    }

    /** 健康检查端点（免认证） */
    @GetMapping("/health")
    fun health(): ResponseEntity<String> = ResponseEntity.ok("OK")

    private fun toResponse(result: DecisionResult): DecisionResponse =
        DecisionResponse(
            decision = result.action.codeName,
            reason = result.reason,
            executionTimeMs = result.executionTimeMs,
            timeout = result.timedOut,
            executionContext = result.executionContext,
        )

    private fun errorResponse(reason: String): DecisionResponse =
        DecisionResponse(
            decision = "REJECT",
            reason = reason,
            executionTimeMs = 0,
            timeout = false,
            executionContext = null,
        )

    companion object {
        /** cache-aware 入口的请求级超时（旧实现固定 50ms） */
        private const val DEFAULT_TIMEOUT_MS = 50L
    }
}
