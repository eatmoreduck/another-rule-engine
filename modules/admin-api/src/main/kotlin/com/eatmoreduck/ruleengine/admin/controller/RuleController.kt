package com.eatmoreduck.ruleengine.admin.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.annotation.SaCheckPermission
import com.eatmoreduck.ruleengine.admin.dto.CreateRuleRequest
import com.eatmoreduck.ruleengine.admin.dto.FeatureValidationResponse
import com.eatmoreduck.ruleengine.admin.dto.PageResponse
import com.eatmoreduck.ruleengine.admin.dto.RuleQuery
import com.eatmoreduck.ruleengine.admin.dto.RuleReferenceResponse
import com.eatmoreduck.ruleengine.admin.dto.RuleResponse
import com.eatmoreduck.ruleengine.admin.dto.UpdateRuleRequest
import com.eatmoreduck.ruleengine.admin.dto.ValidateScriptRequest
import com.eatmoreduck.ruleengine.admin.dto.ValidateScriptResponse
import com.eatmoreduck.ruleengine.admin.feature.FeatureCatalogService
import com.eatmoreduck.ruleengine.admin.rules.PayloadValidation
import com.eatmoreduck.ruleengine.admin.rules.RulePayloadValidator
import com.eatmoreduck.ruleengine.admin.rules.RuleService
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
 * 规则管理 REST API 控制器（路径/方法/权限码与旧 RuleController 一致：/api/v1/rules 全族路径）。。
 */
@RestController
@RequestMapping("/api/v1/rules")
@SaCheckLogin
class RuleController(
    private val ruleService: RuleService,
    private val payloadValidator: RulePayloadValidator,
    private val featureCatalogService: FeatureCatalogService,
) {
    private val log = LoggerFactory.getLogger(RuleController::class.java)

    /** 创建规则：POST /api/v1/rules */
    @PostMapping
    @SaCheckPermission("api:rules:create")
    fun createRule(
        @Valid @RequestBody request: CreateRuleRequest,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<RuleResponse> {
        log.info("创建规则: ruleKey={}, operator={}", request.ruleKey, operator)
        logFeatureValidationResult(request.ruleKey, featureCatalogService.validateGroovyScript(request.groovyScript))
        return ResponseEntity.ok(ruleService.createRule(request, operator))
    }

    /** 更新规则：PUT /api/v1/rules/{ruleKey} */
    @PutMapping("/{ruleKey}")
    @SaCheckPermission("api:rules:update")
    fun updateRule(
        @PathVariable ruleKey: String,
        @Valid @RequestBody request: UpdateRuleRequest,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<RuleResponse> {
        log.info("更新规则: ruleKey={}, operator={}", ruleKey, operator)
        request.groovyScript?.let {
            logFeatureValidationResult(ruleKey, featureCatalogService.validateGroovyScript(it))
        }
        return ResponseEntity.ok(ruleService.updateRule(ruleKey, request, operator))
    }

    /** 删除规则：DELETE /api/v1/rules/{ruleKey} */
    @DeleteMapping("/{ruleKey}")
    @SaCheckPermission("api:rules:delete")
    fun deleteRule(
        @PathVariable ruleKey: String,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<Void> {
        log.info("删除规则: ruleKey={}, operator={}", ruleKey, operator)
        ruleService.deleteRule(ruleKey, operator)
        return ResponseEntity.ok().build()
    }

    /** 启用规则：POST /api/v1/rules/{ruleKey}/enable */
    @PostMapping("/{ruleKey}/enable")
    @SaCheckPermission("api:rules:enable")
    fun enableRule(
        @PathVariable ruleKey: String,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<RuleResponse> {
        log.info("启用规则: ruleKey={}, operator={}", ruleKey, operator)
        return ResponseEntity.ok(ruleService.enableRule(ruleKey, operator))
    }

    /** 禁用规则：POST /api/v1/rules/{ruleKey}/disable */
    @PostMapping("/{ruleKey}/disable")
    @SaCheckPermission("api:rules:disable")
    fun disableRule(
        @PathVariable ruleKey: String,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<RuleResponse> {
        log.info("禁用规则: ruleKey={}, operator={}", ruleKey, operator)
        return ResponseEntity.ok(ruleService.disableRule(ruleKey, operator))
    }

    /** 规则详情：GET /api/v1/rules/{ruleKey} */
    @GetMapping("/{ruleKey}")
    @SaCheckPermission("api:rules:view")
    fun getRule(
        @PathVariable ruleKey: String,
    ): ResponseEntity<RuleResponse> = ResponseEntity.ok(ruleService.getRule(ruleKey))

    /** 规则列表（分页）：GET /api/v1/rules?page=0&size=20&showDeleted=false */
    @GetMapping
    @SaCheckPermission("api:rules:view")
    fun listRules(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(value = "showDeleted", defaultValue = "false") showDeleted: Boolean,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) enabled: Boolean?,
    ): ResponseEntity<PageResponse<RuleResponse>> = ResponseEntity.ok(ruleService.listRules(page, size, showDeleted, keyword, enabled))

    /** 多条件查询：POST /api/v1/rules/query?page=0&size=20 */
    @PostMapping("/query")
    @SaCheckPermission("api:rules:view")
    fun queryRules(
        @RequestBody query: RuleQuery,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<RuleResponse>> {
        log.info("查询规则: query={}", query)
        return ResponseEntity.ok(ruleService.queryRules(query, page, size))
    }

    /** 规则被哪些决策流/规则集引用：GET /api/v1/rules/{ruleKey}/references */
    @GetMapping("/{ruleKey}/references")
    @SaCheckPermission("api:rules:view")
    fun getRuleReferences(
        @PathVariable ruleKey: String,
    ): ResponseEntity<List<RuleReferenceResponse>> {
        log.info("查询规则引用: ruleKey={}", ruleKey)
        return ResponseEntity.ok(ruleService.getRuleReferences(ruleKey))
    }

    /** 验证 Groovy 脚本：POST /api/v1/rules/validate */
    @PostMapping("/validate")
    @SaCheckPermission("api:rules:validate")
    fun validateScript(
        @Valid @RequestBody request: ValidateScriptRequest,
    ): ResponseEntity<ValidateScriptResponse> {
        log.info("验证Groovy脚本")
        return when (val result = payloadValidator.validate(request.groovyScript)) {
            is PayloadValidation.Valid -> {
                ResponseEntity.ok(ValidateScriptResponse.success())
            }

            is PayloadValidation.Invalid -> {
                ResponseEntity.ok(
                    ValidateScriptResponse.error("Groovy脚本语法错误", result.detail),
                )
            }
        }
    }

    private fun logFeatureValidationResult(
        ruleKey: String,
        validation: FeatureValidationResponse,
    ) {
        if (validation.warnings.isEmpty()) {
            return
        }
        log.warn(
            "规则特征校验告警: ruleKey={}, unknownFields={}, warnings={}",
            ruleKey,
            validation.unknownFields,
            validation.warnings,
        )
    }
}
