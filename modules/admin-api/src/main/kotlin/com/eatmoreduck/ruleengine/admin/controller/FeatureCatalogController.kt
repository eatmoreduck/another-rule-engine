package com.eatmoreduck.ruleengine.admin.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.annotation.SaCheckPermission
import com.eatmoreduck.ruleengine.admin.dto.FeatureDefinitionRequest
import com.eatmoreduck.ruleengine.admin.dto.FeatureDefinitionResponse
import com.eatmoreduck.ruleengine.admin.dto.FeatureValidationRequest
import com.eatmoreduck.ruleengine.admin.dto.FeatureValidationResponse
import com.eatmoreduck.ruleengine.admin.dto.PageResponse
import com.eatmoreduck.ruleengine.admin.dto.RuleReferenceResponse
import com.eatmoreduck.ruleengine.admin.feature.FeatureCatalogService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 特征目录 REST API 控制器（路径/方法/权限码与旧 FeatureCatalogController 一致：
 * /api/v1/features/catalog 全族路径）。。
 */
@RestController
@RequestMapping("/api/v1/features/catalog")
@SaCheckLogin
class FeatureCatalogController(
    private val featureCatalogService: FeatureCatalogService,
) {
    /** 目录检索（分页 + 多条件过滤）：GET /api/v1/features/catalog */
    @GetMapping
    @SaCheckPermission("api:feature-catalog:view")
    fun listDefinitions(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) dataType: String?,
        @RequestParam(required = false) sourceType: String?,
        @RequestParam(required = false) sensitivity: String?,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<PageResponse<FeatureDefinitionResponse>> =
        ResponseEntity.ok(
            featureCatalogService.searchDefinitions(page, size, keyword, dataType, sourceType, sensitivity, status),
        )

    /** 特征详情：GET /api/v1/features/catalog/{code} */
    @GetMapping("/{code}")
    @SaCheckPermission("api:feature-catalog:view")
    fun getDefinition(
        @PathVariable code: String,
    ): ResponseEntity<FeatureDefinitionResponse> = ResponseEntity.ok(featureCatalogService.getDefinition(code))

    /** 创建特征：POST /api/v1/features/catalog */
    @PostMapping
    @SaCheckPermission("api:feature-catalog:manage")
    fun createDefinition(
        @Valid @RequestBody request: FeatureDefinitionRequest,
    ): ResponseEntity<FeatureDefinitionResponse> = ResponseEntity.ok(featureCatalogService.createDefinition(request))

    /** 更新特征：PUT /api/v1/features/catalog/{code} */
    @PutMapping("/{code}")
    @SaCheckPermission("api:feature-catalog:manage")
    fun updateDefinition(
        @PathVariable code: String,
        @Valid @RequestBody request: FeatureDefinitionRequest,
    ): ResponseEntity<FeatureDefinitionResponse> = ResponseEntity.ok(featureCatalogService.updateDefinition(code, request))

    /** 批量字段校验：POST /api/v1/features/catalog/validate */
    @PostMapping("/validate")
    @SaCheckPermission("api:feature-catalog:view")
    fun validate(
        @Valid @RequestBody request: FeatureValidationRequest,
    ): ResponseEntity<FeatureValidationResponse> = ResponseEntity.ok(featureCatalogService.validate(request))

    /** 特征被哪些规则/决策流引用：GET /api/v1/features/catalog/{code}/references */
    @GetMapping("/{code}/references")
    @SaCheckPermission("api:feature-catalog:view")
    fun getReferences(
        @PathVariable code: String,
    ): ResponseEntity<List<RuleReferenceResponse>> = ResponseEntity.ok(featureCatalogService.getReferences(code))
}
