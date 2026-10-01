package com.eatmoreduck.ruleengine.admin.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.annotation.SaCheckPermission
import com.eatmoreduck.ruleengine.admin.dto.CreateDecisionFlowRequest
import com.eatmoreduck.ruleengine.admin.dto.DecisionFlowQuery
import com.eatmoreduck.ruleengine.admin.dto.DecisionFlowResponse
import com.eatmoreduck.ruleengine.admin.dto.PageResponse
import com.eatmoreduck.ruleengine.admin.dto.UpdateDecisionFlowRequest
import com.eatmoreduck.ruleengine.admin.flows.DecisionFlowService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 决策流管理 REST API 控制器（路径/方法/权限码与旧 DecisionFlowController 一致）。
 *
 * 说明：旧契约的 `POST /{flowKey}/execute`（流程试执行）属于测试执行域，本批未实现
 * （前端 executeFlowTest 调用点在决策流编辑器的"试运行"按钮，归入后续批次）。
 */
@RestController
@RequestMapping("/api/v1/decision-flows")
@SaCheckLogin
class DecisionFlowController(
    private val flowService: DecisionFlowService,
) {
    private val log = LoggerFactory.getLogger(DecisionFlowController::class.java)

    /** 创建决策流：POST /api/v1/decision-flows */
    @PostMapping
    @SaCheckPermission("api:decision-flows:create")
    fun createFlow(
        @Valid @RequestBody request: CreateDecisionFlowRequest,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<DecisionFlowResponse> {
        log.info("创建决策流: flowKey={}, operator={}", request.flowKey, operator)
        return ResponseEntity.ok(flowService.createFlow(request, operator))
    }

    /** 更新决策流：PUT /api/v1/decision-flows/{flowKey} */
    @PutMapping("/{flowKey}")
    @SaCheckPermission("api:decision-flows:update")
    fun updateFlow(
        @PathVariable flowKey: String,
        @Valid @RequestBody request: UpdateDecisionFlowRequest,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<DecisionFlowResponse> {
        log.info("更新决策流: flowKey={}, operator={}", flowKey, operator)
        return ResponseEntity.ok(flowService.updateFlow(flowKey, request, operator))
    }

    /** 删除决策流（软删除）：DELETE /api/v1/decision-flows/{flowKey} */
    @DeleteMapping("/{flowKey}")
    @SaCheckPermission("api:decision-flows:delete")
    fun deleteFlow(
        @PathVariable flowKey: String,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<Void> {
        log.info("删除决策流: flowKey={}, operator={}", flowKey, operator)
        flowService.deleteFlow(flowKey, operator)
        return ResponseEntity.ok().build()
    }

    /** 启用决策流：POST /api/v1/decision-flows/{flowKey}/enable */
    @PostMapping("/{flowKey}/enable")
    @SaCheckPermission("api:decision-flows:update")
    fun enableFlow(
        @PathVariable flowKey: String,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<DecisionFlowResponse> {
        log.info("启用决策流: flowKey={}, operator={}", flowKey, operator)
        return ResponseEntity.ok(flowService.enableFlow(flowKey, operator))
    }

    /** 禁用决策流：POST /api/v1/decision-flows/{flowKey}/disable */
    @PostMapping("/{flowKey}/disable")
    @SaCheckPermission("api:decision-flows:update")
    fun disableFlow(
        @PathVariable flowKey: String,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<DecisionFlowResponse> {
        log.info("禁用决策流: flowKey={}, operator={}", flowKey, operator)
        return ResponseEntity.ok(flowService.disableFlow(flowKey, operator))
    }

    /** 决策流详情：GET /api/v1/decision-flows/{flowKey} */
    @GetMapping("/{flowKey}")
    @SaCheckPermission("api:decision-flows:view")
    fun getFlow(
        @PathVariable flowKey: String,
    ): ResponseEntity<DecisionFlowResponse> = ResponseEntity.ok(flowService.getFlow(flowKey))

    /** 决策流列表（分页）：GET /api/v1/decision-flows */
    @GetMapping
    @SaCheckPermission("api:decision-flows:view")
    fun listFlows(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<DecisionFlowResponse>> = ResponseEntity.ok(flowService.listFlows(page, size))

    /** 多条件查询（分页）：POST /api/v1/decision-flows/query */
    @PostMapping("/query")
    @SaCheckPermission("api:decision-flows:view")
    fun queryFlows(
        @RequestBody query: DecisionFlowQuery,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<DecisionFlowResponse>> {
        log.info("查询决策流: query={}", query)
        return ResponseEntity.ok(flowService.queryFlows(query, page, size))
    }
}
