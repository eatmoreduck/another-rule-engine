package com.example.ruleengine.controller;

import com.example.ruleengine.domain.DecisionFlow;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.model.dto.CreateRuleRequest;
import com.example.ruleengine.model.dto.RuleQuery;
import com.example.ruleengine.model.dto.RuleReference;
import com.example.ruleengine.model.dto.UpdateRuleRequest;
import com.example.ruleengine.model.dto.ValidateScriptRequest;
import com.example.ruleengine.model.dto.ValidateScriptResponse;
import com.example.ruleengine.model.flow.FlowGraph;
import com.example.ruleengine.model.flow.FlowNodeDef;
import com.example.ruleengine.repository.DecisionFlowRepository;
import com.example.ruleengine.service.lifecycle.RuleLifecycleService;
import com.example.ruleengine.validator.GroovyScriptValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    private final DecisionFlowRepository decisionFlowRepository;
    private final ObjectMapper objectMapper;

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
     * GET /api/v1/rules?page=0&size=20&showDeleted=true&keyword=xxx&enabled=true
     */
    @GetMapping
    public ResponseEntity<Page<Rule>> listRules(
            Pageable pageable,
            @RequestParam(value = "showDeleted", defaultValue = "false") boolean showDeleted,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "enabled", required = false) Boolean enabled) {
        Page<Rule> rules = ruleLifecycleService.listRules(pageable, showDeleted, keyword, enabled);
        return ResponseEntity.ok(rules);
    }

    /**
     * 查询规则（支持多条件过滤）
     * POST /api/v1/rules/query
     */
    @PostMapping("/query")
    public ResponseEntity<Page<Rule>> queryRules(
            @RequestBody RuleQuery query,
            Pageable pageable) {
        log.info("查询规则: query={}", query);
        Page<Rule> rules = ruleLifecycleService.queryRules(query, pageable);
        return ResponseEntity.ok(rules);
    }

    /**
     * 查询规则被哪些决策流/规则集引用
     * GET /api/v1/rules/{ruleKey}/references
     */
    @GetMapping("/{ruleKey}/references")
    public ResponseEntity<List<RuleReference>> getRuleReferences(@PathVariable String ruleKey) {
        log.info("查询规则引用: ruleKey={}", ruleKey);

        List<RuleReference> references = new ArrayList<>();
        List<DecisionFlow> allFlows = decisionFlowRepository.findAll();

        for (DecisionFlow flow : allFlows) {
            try {
                FlowGraph graph = objectMapper.readValue(flow.getFlowGraph(), FlowGraph.class);
                if (graph.getNodes() == null) {
                    continue;
                }
                for (FlowNodeDef node : graph.getNodes()) {
                    if (!"ruleset".equals(node.getType()) || node.getData() == null) {
                        continue;
                    }
                    Object ruleKeysObj = node.getData().get("ruleKeys");
                    if (ruleKeysObj instanceof List<?> ruleKeys) {
                        boolean matches = ruleKeys.stream()
                                .anyMatch(key -> ruleKey.equals(key != null ? key.toString() : null));
                        if (matches) {
                            references.add(RuleReference.builder()
                                    .type("decision_flow")
                                    .id(flow.getId())
                                    .name(flow.getFlowName())
                                    .key(flow.getFlowKey())
                                    .build());
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析决策流定义失败: flowKey={}, error={}", flow.getFlowKey(), e.getMessage());
            }
        }

        return ResponseEntity.ok(references);
    }

    /**
     * 验证 Groovy 脚本
     * POST /api/v1/rules/validate
     */
    @PostMapping("/validate")
    public ResponseEntity<ValidateScriptResponse> validateScript(
            @Valid @RequestBody ValidateScriptRequest request) {
        log.info("验证Groovy脚本");
        GroovyScriptValidator.ValidationResult validation = scriptValidator.validate(request.getGroovyScript());

        if (validation.isValid()) {
            return ResponseEntity.ok(ValidateScriptResponse.success());
        } else {
            return ResponseEntity.ok(ValidateScriptResponse.error(
                    "Groovy脚本语法错误",
                    validation.getErrorMessage()
            ));
        }
    }
}
