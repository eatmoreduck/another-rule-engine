package com.example.ruleengine.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.ruleengine.domain.DecisionFlowVersion;
import com.example.ruleengine.service.version.DecisionFlowVersionManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 决策流版本管理 API 控制器
 */
@RestController
@RequestMapping("/api/v1/decision-flows/{flowKey}/versions")
@RequiredArgsConstructor
@Slf4j
@SaCheckLogin
public class DecisionFlowVersionController {

    private final DecisionFlowVersionManagementService versionService;

    /**
     * 查询决策流的所有版本
     */
    @GetMapping
    @SaCheckPermission("api:decision-flows:view")
    public ResponseEntity<List<DecisionFlowVersion>> listVersions(@PathVariable String flowKey) {
        List<DecisionFlowVersion> versions = versionService.listVersions(flowKey);
        return ResponseEntity.ok(versions);
    }

    /**
     * 查询决策流的指定版本
     */
    @GetMapping("/{version}")
    @SaCheckPermission("api:decision-flows:view")
    public ResponseEntity<DecisionFlowVersion> getVersion(
            @PathVariable String flowKey,
            @PathVariable Integer version) {
        return versionService.getVersion(flowKey, version)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 回滚决策流到指定版本
     */
    @PostMapping("/rollback")
    @SaCheckPermission("api:decision-flows:update")
    public ResponseEntity<DecisionFlowVersion> rollback(
            @PathVariable String flowKey,
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        Integer targetVersion = (Integer) request.get("targetVersion");
        log.info("回滚决策流版本: flowKey={}, targetVersion={}", flowKey, targetVersion);
        DecisionFlowVersion version = versionService.rollback(flowKey, targetVersion, operator);
        return ResponseEntity.ok(version);
    }
}
