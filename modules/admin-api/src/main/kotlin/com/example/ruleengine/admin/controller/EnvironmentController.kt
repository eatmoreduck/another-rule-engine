package com.example.ruleengine.admin.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.example.ruleengine.admin.dto.CloneEnvironmentRequest
import com.example.ruleengine.admin.dto.CloneEnvironmentResponse
import com.example.ruleengine.admin.dto.EnvironmentResponse
import com.example.ruleengine.admin.dto.RuleResponse
import com.example.ruleengine.admin.environment.EnvironmentService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 环境管理 REST API 控制器（路径/方法与旧 EnvironmentController 一致）。
 *
 * 说明：旧实现有 @ConditionalOnProperty(multi-environment.enabled) 且旧默认配置为
 * false（接口整体 404）；新实现无条件装配使环境页可用（行为差异见汇报）。
 * 仅要求登录（旧实现即无权限注解）。
 */
@RestController
@RequestMapping("/api/v1/environments")
@SaCheckLogin
class EnvironmentController(
    private val environmentService: EnvironmentService,
) {
    private val log = LoggerFactory.getLogger(EnvironmentController::class.java)

    /** 获取环境列表：GET /api/v1/environments */
    @GetMapping
    fun listEnvironments(): ResponseEntity<List<EnvironmentResponse>> = ResponseEntity.ok(environmentService.listEnvironments())

    /** 获取环境详情：GET /api/v1/environments/{id} */
    @GetMapping("/{id}")
    fun getEnvironment(
        @PathVariable id: Long,
    ): ResponseEntity<EnvironmentResponse> = ResponseEntity.ok(environmentService.getEnvironment(id))

    /** 获取环境下的规则：GET /api/v1/environments/{id}/rules */
    @GetMapping("/{id}/rules")
    fun getEnvironmentRules(
        @PathVariable id: Long,
    ): ResponseEntity<List<RuleResponse>> = ResponseEntity.ok(environmentService.getRulesByEnvironment(id))

    /** 克隆环境规则（from/to 为环境名称）：POST /api/v1/environments/{from}/clone/{to} */
    @PostMapping("/{from}/clone/{to}")
    fun cloneEnvironmentRules(
        @PathVariable from: String,
        @PathVariable to: String,
        @RequestBody(required = false) request: CloneEnvironmentRequest?,
    ): ResponseEntity<CloneEnvironmentResponse> {
        log.info("克隆环境规则: from={}, to={}", from, to)
        // 缺省值解析照旧 Controller：overwrite 默认 false，operator 缺省 "system"
        val overwrite = request?.overwrite == true
        val operator = request?.operator?.takeIf { it.isNotEmpty() } ?: "system"
        return ResponseEntity.ok(environmentService.cloneEnvironmentRules(from, to, overwrite, operator))
    }
}
