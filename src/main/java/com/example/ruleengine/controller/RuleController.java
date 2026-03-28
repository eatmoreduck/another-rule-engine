package com.example.ruleengine.controller;

import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.model.dto.CreateRuleRequest;
import com.example.ruleengine.model.dto.UpdateRuleRequest;
import com.example.ruleengine.service.lifecycle.RuleLifecycleService;
import com.example.ruleengine.validator.GroovyScriptValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 规则管理 REST API 控制器
 */
@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
@Slf4j
public class RuleController {

    private final RuleLifecycleService ruleLifecycleService;
    private final GroovyScriptValidator scriptValidator;

    /**
     * 创建规则
     * POST /api/v1/rules
     */
    @PostMapping
    public ResponseEntity<Rule> createRule(
            @Valid @RequestBody CreateRuleRequest request,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        log.info("创建规则: ruleKey={}, operator={}", request.getRuleKey(), operator);
        Rule rule = ruleLifecycleService.createRule(request, operator);
        return ResponseEntity.ok(rule);
    }

    /**
     * 更新规则
     * PUT /api/v1/rules/{ruleKey}
     */
    @PutMapping("/{ruleKey}")
    public ResponseEntity<Rule> updateRule(
            @PathVariable String ruleKey,
            @Valid @RequestBody UpdateRuleRequest request,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        log.info("更新规则: ruleKey={}, operator={}", ruleKey, operator);
        Rule rule = ruleLifecycleService.updateRule(ruleKey, request, operator);
        return ResponseEntity.ok(rule);
    }

    /**
     * 删除规则
     * DELETE /api/v1/rules/{ruleKey}
     */
    @DeleteMapping("/{ruleKey}")
    public ResponseEntity<Void> deleteRule(
            @PathVariable String ruleKey,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        log.info("删除规则: ruleKey={}, operator={}", ruleKey, operator);
        ruleLifecycleService.deleteRule(ruleKey, operator);
        return ResponseEntity.ok().build();
    }

    /**
     * 启用规则
     * POST /api/v1/rules/{ruleKey}/enable
     */
    @PostMapping("/{ruleKey}/enable")
    public ResponseEntity<Rule> enableRule(
            @PathVariable String ruleKey,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        log.info("启用规则: ruleKey={}, operator={}", ruleKey, operator);
        Rule rule = ruleLifecycleService.enableRule(ruleKey, operator);
        return ResponseEntity.ok(rule);
    }

    /**
     * 禁用规则
     * POST /api/v1/rules/{ruleKey}/disable
     */
    @PostMapping("/{ruleKey}/disable")
    public ResponseEntity<Rule> disableRule(
            @PathVariable String ruleKey,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        log.info("禁用规则: ruleKey={}, operator={}", ruleKey, operator);
        Rule rule = ruleLifecycleService.disableRule(ruleKey, operator);
        return ResponseEntity.ok(rule);
    }

    /**
     * 获取规则详情
     * GET /api/v1/rules/{ruleKey}
     */
    @GetMapping("/{ruleKey}")
    public ResponseEntity<Rule> getRule(@PathVariable String ruleKey) {
        Rule rule = ruleLifecycleService.getRule(ruleKey);
        return ResponseEntity.ok(rule);
    }

    /**
     * 列出所有规则（分页）
     * GET /api/v1/rules?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<Page<Rule>> listRules(Pageable pageable) {
        Page<Rule> rules = ruleLifecycleService.listRules(pageable);
        return ResponseEntity.ok(rules);
    }
}
