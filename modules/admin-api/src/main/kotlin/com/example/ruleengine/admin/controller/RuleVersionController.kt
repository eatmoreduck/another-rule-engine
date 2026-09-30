package com.example.ruleengine.admin.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.annotation.SaCheckPermission
import com.example.ruleengine.admin.dto.CreateVersionRequest
import com.example.ruleengine.admin.dto.RollbackRequest
import com.example.ruleengine.admin.dto.VersionDiffResponse
import com.example.ruleengine.admin.dto.VersionResponse
import com.example.ruleengine.admin.rules.VersionService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 规则版本管理 REST API 控制器（路径/方法/权限码与旧 RuleVersionController 一致）。
 */
@RestController
@RequestMapping("/api/v1/rules")
@SaCheckLogin
class RuleVersionController(
    private val versionService: VersionService,
) {
    private val log = LoggerFactory.getLogger(RuleVersionController::class.java)

    /** 获取规则的所有版本：GET /api/v1/rules/{ruleKey}/versions */
    @GetMapping("/{ruleKey}/versions")
    @SaCheckPermission("api:rules:view")
    fun getVersions(
        @PathVariable ruleKey: String,
    ): ResponseEntity<List<VersionResponse>> {
        log.info("获取规则版本列表: ruleKey={}", ruleKey)
        return ResponseEntity.ok(versionService.getVersions(ruleKey))
    }

    /** 获取规则的特定版本：GET /api/v1/rules/{ruleKey}/versions/{version} */
    @GetMapping("/{ruleKey}/versions/{version}")
    @SaCheckPermission("api:rules:view")
    fun getVersion(
        @PathVariable ruleKey: String,
        @PathVariable version: Int,
    ): ResponseEntity<VersionResponse> {
        log.info("获取规则特定版本: ruleKey={}, version={}", ruleKey, version)
        return ResponseEntity.ok(versionService.getVersion(ruleKey, version))
    }

    /** 创建新版本：POST /api/v1/rules/{ruleKey}/versions */
    @PostMapping("/{ruleKey}/versions")
    @SaCheckPermission("api:rules:update")
    fun createVersion(
        @PathVariable ruleKey: String,
        @Valid @RequestBody request: CreateVersionRequest,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<VersionResponse> {
        log.info("创建规则新版本: ruleKey={}, operator={}", ruleKey, operator)
        return ResponseEntity.ok(versionService.createVersion(ruleKey, request, operator))
    }

    /** 回滚到指定版本：POST /api/v1/rules/{ruleKey}/versions/{version}/rollback */
    @PostMapping("/{ruleKey}/versions/{version}/rollback")
    @SaCheckPermission("api:rules:update")
    fun rollbackToVersion(
        @PathVariable ruleKey: String,
        @PathVariable version: Int,
        @Valid @RequestBody request: RollbackRequest,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<VersionResponse> {
        log.info("回滚规则版本: ruleKey={}, targetVersion={}, operator={}", ruleKey, version, operator)
        return ResponseEntity.ok(versionService.rollbackToVersion(ruleKey, version, request, operator))
    }

    /** 比较两个版本：GET /api/v1/rules/{ruleKey}/versions/compare?version1=1&version2=2 */
    @GetMapping("/{ruleKey}/versions/compare")
    @SaCheckPermission("api:rules:view")
    fun compareVersions(
        @PathVariable ruleKey: String,
        @RequestParam version1: Int,
        @RequestParam version2: Int,
    ): ResponseEntity<VersionDiffResponse> {
        log.info("比较规则版本: ruleKey={}, version1={}, version2={}", ruleKey, version1, version2)
        return ResponseEntity.ok(versionService.compareVersions(ruleKey, version1, version2))
    }
}
