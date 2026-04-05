package com.example.ruleengine.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.ruleengine.model.dto.CreateVersionRequest;
import com.example.ruleengine.model.dto.RollbackRequest;
import com.example.ruleengine.model.dto.VersionDiffResponse;
import com.example.ruleengine.model.dto.VersionResponse;
import com.example.ruleengine.service.version.VersionManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 规则版本管理 REST API 控制器
 */
@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
@Slf4j
@SaCheckLogin
public class RuleVersionController {

    private final VersionManagementService versionManagementService;

    /**
     * 获取规则的所有版本
     * GET /api/v1/rules/{ruleKey}/versions
     */
    @GetMapping("/{ruleKey}/versions")
    @SaCheckPermission("api:rules:view")
    public ResponseEntity<List<VersionResponse>> getVersions(@PathVariable String ruleKey) {
        log.info("获取规则版本列表: ruleKey={}", ruleKey);
        List<VersionResponse> versions = versionManagementService.getVersions(ruleKey);
        return ResponseEntity.ok(versions);
    }

    /**
     * 获取规则的特定版本
     * GET /api/v1/rules/{ruleKey}/versions/{version}
     */
    @GetMapping("/{ruleKey}/versions/{version}")
    @SaCheckPermission("api:rules:view")
    public ResponseEntity<VersionResponse> getVersion(
            @PathVariable String ruleKey,
            @PathVariable Integer version) {
        log.info("获取规则特定版本: ruleKey={}, version={}", ruleKey, version);
        VersionResponse versionResponse = versionManagementService.getVersion(ruleKey, version);
        return ResponseEntity.ok(versionResponse);
    }

    /**
     * 创建新版本
     * POST /api/v1/rules/{ruleKey}/versions
     */
    @PostMapping("/{ruleKey}/versions")
    @SaCheckPermission("api:rules:update")
    public ResponseEntity<VersionResponse> createVersion(
            @PathVariable String ruleKey,
            @Valid @RequestBody CreateVersionRequest request,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        log.info("创建规则新版本: ruleKey={}, operator={}", ruleKey, operator);
        VersionResponse versionResponse = versionManagementService.createVersion(
                ruleKey, request, operator);
        return ResponseEntity.ok(versionResponse);
    }

    /**
     * 回滚到指定版本
     * POST /api/v1/rules/{ruleKey}/versions/{version}/rollback
     */
    @PostMapping("/{ruleKey}/versions/{version}/rollback")
    @SaCheckPermission("api:rules:update")
    public ResponseEntity<VersionResponse> rollbackToVersion(
            @PathVariable String ruleKey,
            @PathVariable Integer version,
            @Valid @RequestBody RollbackRequest request,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        log.info("回滚规则版本: ruleKey={}, targetVersion={}, operator={}", ruleKey, version, operator);
        VersionResponse versionResponse = versionManagementService.rollbackToVersion(
                ruleKey, version, operator);
        return ResponseEntity.ok(versionResponse);
    }

    /**
     * 比较两个版本
     * GET /api/v1/rules/{ruleKey}/versions/compare?version1=1&version2=2
     */
    @GetMapping("/{ruleKey}/versions/compare")
    @SaCheckPermission("api:rules:view")
    public ResponseEntity<VersionDiffResponse> compareVersions(
            @PathVariable String ruleKey,
            @RequestParam Integer version1,
            @RequestParam Integer version2) {
        log.info("比较规则版本: ruleKey={}, version1={}, version2={}", ruleKey, version1, version2);
        VersionDiffResponse diffResponse = versionManagementService.compareVersions(
                ruleKey, version1, version2);
        return ResponseEntity.ok(diffResponse);
    }
}
