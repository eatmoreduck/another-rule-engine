package com.example.ruleengine.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.ruleengine.domain.Environment;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.model.dto.CloneEnvironmentRequest;
import com.example.ruleengine.model.dto.CloneEnvironmentResponse;
import com.example.ruleengine.service.environment.EnvironmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 环境管理 REST API 控制器
 */
@RestController
@RequestMapping("/api/v1/environments")
@RequiredArgsConstructor
@Slf4j
@SaCheckLogin
public class EnvironmentController {

    private final EnvironmentService environmentService;

    /**
     * 获取环境列表
     * GET /api/v1/environments
     */
    @GetMapping
    public ResponseEntity<List<Environment>> listEnvironments() {
        List<Environment> environments = environmentService.listEnvironments();
        return ResponseEntity.ok(environments);
    }

    /**
     * 获取环境详情
     * GET /api/v1/environments/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Environment> getEnvironment(@PathVariable Long id) {
        Environment environment = environmentService.getEnvironment(id);
        return ResponseEntity.ok(environment);
    }

    /**
     * 获取环境下的规则
     * GET /api/v1/environments/{id}/rules
     */
    @GetMapping("/{id}/rules")
    public ResponseEntity<List<Rule>> getEnvironmentRules(@PathVariable Long id) {
        List<Rule> rules = environmentService.getRulesByEnvironment(id);
        return ResponseEntity.ok(rules);
    }

    /**
     * 克隆环境规则
     * POST /api/v1/environments/{from}/clone/{to}
     */
    @PostMapping("/{from}/clone/{to}")
    public ResponseEntity<CloneEnvironmentResponse> cloneEnvironmentRules(
            @PathVariable String from,
            @PathVariable String to,
            @RequestBody(required = false) CloneEnvironmentRequest request) {
        log.info("克隆环境规则: from={}, to={}", from, to);

        boolean overwrite = request != null && Boolean.TRUE.equals(request.getOverwrite());
        String operator = request != null && request.getOperator() != null ? request.getOperator() : "system";

        CloneEnvironmentResponse response = environmentService.cloneEnvironmentRules(from, to, overwrite, operator);
        return ResponseEntity.ok(response);
    }
}
