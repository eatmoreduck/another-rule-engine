package com.example.ruleengine.controller;

import com.example.ruleengine.model.dto.ImportRulesResponse;
import com.example.ruleengine.model.dto.RuleExportData;
import com.example.ruleengine.service.importexport.RuleImportExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 规则导入导出 REST API 控制器
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ImportExportController {

    private final RuleImportExportService ruleImportExportService;

    /**
     * 导出所有规则
     * GET /api/v1/export/rules
     */
    @GetMapping("/export/rules")
    public ResponseEntity<RuleExportData> exportAllRules(
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        log.info("导出所有规则, operator={}", operator);
        RuleExportData exportData = ruleImportExportService.exportAllRules(operator);
        return ResponseEntity.ok(exportData);
    }

    /**
     * 导出单条规则
     * GET /api/v1/export/rules/{ruleKey}
     */
    @GetMapping("/export/rules/{ruleKey}")
    public ResponseEntity<RuleExportData> exportRule(
            @PathVariable String ruleKey,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        log.info("导出规则: ruleKey={}, operator={}", ruleKey, operator);
        RuleExportData exportData = ruleImportExportService.exportRule(ruleKey, operator);
        return ResponseEntity.ok(exportData);
    }

    /**
     * 批量导出规则
     * POST /api/v1/export/rules/batch
     */
    @PostMapping("/export/rules/batch")
    public ResponseEntity<RuleExportData> exportRules(
            @RequestBody List<String> ruleKeys,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        log.info("批量导出规则: count={}, operator={}", ruleKeys.size(), operator);
        RuleExportData exportData = ruleImportExportService.exportRules(ruleKeys, operator);
        return ResponseEntity.ok(exportData);
    }

    /**
     * 导入规则
     * POST /api/v1/import/rules
     */
    @PostMapping("/import/rules")
    public ResponseEntity<ImportRulesResponse> importRules(
            @RequestBody RuleExportData exportData,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        log.info("导入规则, operator={}", operator);
        ImportRulesResponse response = ruleImportExportService.importRules(exportData, operator);
        return ResponseEntity.ok(response);
    }
}
