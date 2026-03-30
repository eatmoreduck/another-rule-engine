package com.example.ruleengine.controller;

import com.example.ruleengine.domain.CustomTemplate;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.domain.RuleTemplate;
import com.example.ruleengine.model.dto.CreateCustomTemplateRequest;
import com.example.ruleengine.model.dto.CreateRuleRequest;
import com.example.ruleengine.model.dto.InstantiateTemplateRequest;
import com.example.ruleengine.service.lifecycle.RuleLifecycleService;
import com.example.ruleengine.service.template.RuleTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 规则模板 REST API 控制器
 */
@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
@Slf4j
public class TemplateController {

    private final RuleTemplateService ruleTemplateService;
    private final RuleLifecycleService ruleLifecycleService;

    // ========== 系统模板 (RCONF-03) ==========

    /**
     * 获取系统模板列表
     * GET /api/v1/templates
     */
    @GetMapping
    public ResponseEntity<List<RuleTemplate>> listTemplates(
            @RequestParam(required = false) String category) {
        if (category != null) {
            return ResponseEntity.ok(ruleTemplateService.getTemplatesByCategory(category));
        }
        return ResponseEntity.ok(ruleTemplateService.getTemplates());
    }

    /**
     * 获取系统模板详情
     * GET /api/v1/templates/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<RuleTemplate> getTemplate(@PathVariable Long id) {
        RuleTemplate template = ruleTemplateService.getTemplate(id);
        return ResponseEntity.ok(template);
    }

    /**
     * 从系统模板实例化规则
     * POST /api/v1/templates/{id}/instantiate
     */
    @PostMapping("/{id}/instantiate")
    public ResponseEntity<Rule> instantiateFromTemplate(
            @PathVariable Long id,
            @Valid @RequestBody InstantiateTemplateRequest request) {
        log.info("从模板创建规则: templateId={}, ruleKey={}", id, request.getRuleKey());

        // 从模板生成 Rule 对象
        Rule rule = ruleTemplateService.createFromTemplate(id, request);

        // 通过 RuleLifecycleService 保存（包含脚本验证等）
        String operator = request.getOperator() != null ? request.getOperator() : "system";
        CreateRuleRequest createRequest = CreateRuleRequest.builder()
                .ruleKey(rule.getRuleKey())
                .ruleName(rule.getRuleName())
                .ruleDescription(rule.getRuleDescription())
                .groovyScript(rule.getGroovyScript())
                .build();
        Rule created = ruleLifecycleService.createRule(createRequest, operator);

        return ResponseEntity.ok(created);
    }

    // ========== 个人模板 (RCONF-04) ==========

    /**
     * 保存个人模板
     * POST /api/v1/templates/custom
     */
    @PostMapping("/custom")
    public ResponseEntity<CustomTemplate> saveCustomTemplate(
            @Valid @RequestBody CreateCustomTemplateRequest request,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        log.info("保存个人模板: name={}, operator={}", request.getName(), operator);
        if (request.getCreatedBy() == null) {
            request.setCreatedBy(operator);
        }
        CustomTemplate saved = ruleTemplateService.saveCustomTemplate(request);
        return ResponseEntity.ok(saved);
    }

    /**
     * 获取个人模板列表
     * GET /api/v1/templates/custom
     */
    @GetMapping("/custom")
    public ResponseEntity<List<CustomTemplate>> listCustomTemplates(
            @RequestParam(required = false) String createdBy) {
        List<CustomTemplate> templates = ruleTemplateService.getCustomTemplates(createdBy);
        return ResponseEntity.ok(templates);
    }

    /**
     * 获取个人模板详情
     * GET /api/v1/templates/custom/{id}
     */
    @GetMapping("/custom/{id}")
    public ResponseEntity<CustomTemplate> getCustomTemplate(@PathVariable Long id) {
        CustomTemplate template = ruleTemplateService.getCustomTemplate(id);
        return ResponseEntity.ok(template);
    }

    /**
     * 从个人模板实例化规则
     * POST /api/v1/templates/custom/{id}/instantiate
     */
    @PostMapping("/custom/{id}/instantiate")
    public ResponseEntity<Rule> instantiateFromCustomTemplate(
            @PathVariable Long id,
            @Valid @RequestBody InstantiateTemplateRequest request) {
        log.info("从个人模板创建规则: templateId={}, ruleKey={}", id, request.getRuleKey());

        Rule rule = ruleTemplateService.createFromCustomTemplate(id, request);

        String operator = request.getOperator() != null ? request.getOperator() : "system";
        CreateRuleRequest createRequest = CreateRuleRequest.builder()
                .ruleKey(rule.getRuleKey())
                .ruleName(rule.getRuleName())
                .ruleDescription(rule.getRuleDescription())
                .groovyScript(rule.getGroovyScript())
                .build();
        Rule created = ruleLifecycleService.createRule(createRequest, operator);

        return ResponseEntity.ok(created);
    }

    /**
     * 删除个人模板
     * DELETE /api/v1/templates/custom/{id}
     */
    @DeleteMapping("/custom/{id}")
    public ResponseEntity<Void> deleteCustomTemplate(@PathVariable Long id) {
        log.info("删除个人模板: id={}", id);
        ruleTemplateService.deleteCustomTemplate(id);
        return ResponseEntity.ok().build();
    }
}
