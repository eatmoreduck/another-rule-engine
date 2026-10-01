package com.eatmoreduck.ruleengine.admin.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.annotation.SaCheckPermission
import com.eatmoreduck.ruleengine.admin.dto.CreateFlowVersionRequest
import com.eatmoreduck.ruleengine.admin.dto.DecisionFlowVersionResponse
import com.eatmoreduck.ruleengine.admin.dto.FlowRollbackRequest
import com.eatmoreduck.ruleengine.admin.flows.DecisionFlowService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 决策流版本管理 REST API 控制器（路径/方法/权限码与旧 DecisionFlowVersionController 一致）。
 *
 * 说明：新建草稿版本与发布为旧服务层已有、控制器未暴露的能力（本批按任务要求补齐端点），
 * 路径形状对齐 2b 规则版本控制器（`POST /{flowKey}/versions`、`POST /{flowKey}/versions/{version}/publish`）；
 * 前端现有调用面（versions 列表 / rollback）不受影响。
 */
@RestController
@RequestMapping("/api/v1/decision-flows/{flowKey}/versions")
@SaCheckLogin
class DecisionFlowVersionController(
    private val flowService: DecisionFlowService,
) {
    private val log = LoggerFactory.getLogger(DecisionFlowVersionController::class.java)

    /** 查询决策流的所有版本：GET /api/v1/decision-flows/{flowKey}/versions */
    @GetMapping
    @SaCheckPermission("api:decision-flows:view")
    fun listVersions(
        @PathVariable flowKey: String,
    ): ResponseEntity<List<DecisionFlowVersionResponse>> = ResponseEntity.ok(flowService.getVersions(flowKey))

    /** 查询决策流的指定版本：GET /api/v1/decision-flows/{flowKey}/versions/{version}（缺失 → 404 空体，照旧） */
    @GetMapping("/{version}")
    @SaCheckPermission("api:decision-flows:view")
    fun getVersion(
        @PathVariable flowKey: String,
        @PathVariable version: Int,
    ): ResponseEntity<DecisionFlowVersionResponse> {
        val versionResponse = flowService.getVersion(flowKey, version)
        return if (versionResponse != null) ResponseEntity.ok(versionResponse) else ResponseEntity.notFound().build()
    }

    /** 新建草稿版本（DRAFT，不改变生效版本）：POST /api/v1/decision-flows/{flowKey}/versions */
    @PostMapping
    @SaCheckPermission("api:decision-flows:update")
    fun createDraftVersion(
        @PathVariable flowKey: String,
        @Valid @RequestBody request: CreateFlowVersionRequest,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<DecisionFlowVersionResponse> {
        log.info("创建决策流草稿版本: flowKey={}, operator={}", flowKey, operator)
        return ResponseEntity.ok(flowService.createDraftVersion(flowKey, request, operator))
    }

    /** 发布决策流版本（DRAFT/CANARY → ACTIVE）：POST /api/v1/decision-flows/{flowKey}/versions/{version}/publish */
    @PostMapping("/{version}/publish")
    @SaCheckPermission("api:decision-flows:update")
    fun publishVersion(
        @PathVariable flowKey: String,
        @PathVariable version: Int,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<DecisionFlowVersionResponse> {
        log.info("发布决策流版本: flowKey={}, version={}, operator={}", flowKey, version, operator)
        return ResponseEntity.ok(flowService.publishVersion(flowKey, version, operator))
    }

    /** 回滚决策流到指定版本：POST /api/v1/decision-flows/{flowKey}/versions/rollback，请求体 `{"targetVersion": N}` */
    @PostMapping("/rollback")
    @SaCheckPermission("api:decision-flows:update")
    fun rollback(
        @PathVariable flowKey: String,
        @RequestBody request: FlowRollbackRequest,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<DecisionFlowVersionResponse> {
        log.info("回滚决策流版本: flowKey={}, targetVersion={}, operator={}", flowKey, request.targetVersion, operator)
        return ResponseEntity.ok(flowService.rollback(flowKey, request, operator))
    }
}
