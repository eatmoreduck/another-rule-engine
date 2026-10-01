package com.eatmoreduck.ruleengine.admin.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.eatmoreduck.ruleengine.admin.dto.ImportRulesResponse
import com.eatmoreduck.ruleengine.admin.dto.RuleExportData
import com.eatmoreduck.ruleengine.admin.importexport.RuleImportExportService
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
 * 规则导入导出 REST API 控制器（路径/方法/请求响应结构与旧 ImportExportController 一致：
 * /api/v1/export/rules 族 + /api/v1/import/rules）。
 *
 * 鉴权口径与旧一致：类级登录校验，无方法级权限码。
 *
 * 与旧实现的差异：旧控制器/服务由 rule-engine.features.import-export.enabled 配置门控
 * （旧默认配置即关闭，端点不注册）；新实现常驻注册，功能开关 GET /api/v1/features 的
 * importExport 值保持既有契约不变（false，控制前端菜单显隐）。
 */
@RestController
@RequestMapping("/api/v1")
@SaCheckLogin
class ImportExportController(
    private val ruleImportExportService: RuleImportExportService,
) {
    private val log = LoggerFactory.getLogger(ImportExportController::class.java)

    /** 导出所有规则：GET /api/v1/export/rules */
    @GetMapping("/export/rules")
    fun exportAllRules(
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<RuleExportData> {
        log.info("导出所有规则, operator={}", operator)
        return ResponseEntity.ok(ruleImportExportService.exportAllRules(operator))
    }

    /** 导出单条规则：GET /api/v1/export/rules/{ruleKey} */
    @GetMapping("/export/rules/{ruleKey}")
    fun exportRule(
        @PathVariable ruleKey: String,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<RuleExportData> {
        log.info("导出规则: ruleKey={}, operator={}", ruleKey, operator)
        return ResponseEntity.ok(ruleImportExportService.exportRule(ruleKey, operator))
    }

    /** 批量导出规则：POST /api/v1/export/rules/batch（请求体为 ruleKey 数组） */
    @PostMapping("/export/rules/batch")
    fun exportRules(
        @RequestBody ruleKeys: List<String>,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<RuleExportData> {
        log.info("批量导出规则: count={}, operator={}", ruleKeys.size, operator)
        return ResponseEntity.ok(ruleImportExportService.exportRules(ruleKeys, operator))
    }

    /** 导入规则：POST /api/v1/import/rules（请求体为导出文件原样回传） */
    @PostMapping("/import/rules")
    fun importRules(
        @RequestBody exportData: RuleExportData,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<ImportRulesResponse> {
        log.info("导入规则, operator={}", operator)
        return ResponseEntity.ok(ruleImportExportService.importRules(exportData, operator))
    }
}
