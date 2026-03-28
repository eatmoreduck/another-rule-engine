package com.example.ruleengine.service.lifecycle;

import com.example.ruleengine.annotation.Auditable;
import com.example.ruleengine.constants.AuditEvent;
import com.example.ruleengine.constants.RuleStatus;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.model.dto.CreateRuleRequest;
import com.example.ruleengine.model.dto.UpdateRuleRequest;
import com.example.ruleengine.repository.RuleRepository;
import com.example.ruleengine.service.version.VersionManagementService;
import com.example.ruleengine.validator.GroovyScriptValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 规则生命周期管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleLifecycleService {

    private final RuleRepository ruleRepository;
    private final GroovyScriptValidator scriptValidator;
    private final VersionManagementService versionManagementService;

    /**
     * 创建新规则
     */
    @Auditable(event = AuditEvent.RULE_CREATE, entityType = "RULE", entityIdExpression = "#request.ruleKey")
    @Transactional
    public Rule createRule(CreateRuleRequest request, String operator) {
        // 1. 验证 rule_key 唯一性
        if (ruleRepository.existsByRuleKey(request.getRuleKey())) {
            throw new IllegalArgumentException("规则Key已存在: " + request.getRuleKey());
        }

        // 2. 验证 Groovy 脚本语法
        GroovyScriptValidator.ValidationResult validation = scriptValidator.validate(request.getGroovyScript());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Groovy脚本语法错误: " + validation.getErrorMessage());
        }

        // 3. 创建规则实体
        Rule rule = Rule.builder()
                .ruleKey(request.getRuleKey())
                .ruleName(request.getRuleName())
                .ruleDescription(request.getRuleDescription())
                .groovyScript(request.getGroovyScript())
                .version(1)
                .status(RuleStatus.DRAFT)
                .createdBy(operator)
                .enabled(true)
                .build();

        Rule saved = ruleRepository.save(rule);
        log.info("创建规则: ruleKey={}, operator={}", request.getRuleKey(), operator);
        return saved;
    }

    /**
     * 更新规则（自动创建新版本）
     */
    @Auditable(event = AuditEvent.RULE_UPDATE, entityType = "RULE", entityIdExpression = "#ruleKey")
    @Transactional
    public Rule updateRule(String ruleKey, UpdateRuleRequest request, String operator) {
        // 1. 验证规则存在
        Rule rule = ruleRepository.findByRuleKey(ruleKey)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + ruleKey));

        // 2. 验证 Groovy 脚本语法
        if (request.getGroovyScript() != null) {
            GroovyScriptValidator.ValidationResult validation = scriptValidator.validate(request.getGroovyScript());
            if (!validation.isValid()) {
                throw new IllegalArgumentException("Groovy脚本语法错误: " + validation.getErrorMessage());
            }
        }

        // 3. 更新规则
        if (request.getRuleName() != null) {
            rule.setRuleName(request.getRuleName());
        }
        if (request.getRuleDescription() != null) {
            rule.setRuleDescription(request.getRuleDescription());
        }
        if (request.getGroovyScript() != null) {
            rule.setGroovyScript(request.getGroovyScript());
        }
        rule.setUpdatedBy(operator);

        Rule updated = ruleRepository.save(rule);
        log.info("更新规则: ruleKey={}, operator={}", ruleKey, operator);
        return updated;
    }

    /**
     * 删除规则（软删除）
     */
    @Auditable(event = AuditEvent.RULE_DELETE, entityType = "RULE", entityIdExpression = "#ruleKey")
    @Transactional
    public void deleteRule(String ruleKey, String operator) {
        Rule rule = ruleRepository.findByRuleKey(ruleKey)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + ruleKey));
        rule.setStatus(RuleStatus.DELETED);
        rule.setUpdatedBy(operator);
        ruleRepository.save(rule);
        log.info("删除规则: ruleKey={}, operator={}", ruleKey, operator);
    }

    /**
     * 启用规则
     */
    @Auditable(event = AuditEvent.RULE_ENABLE, entityType = "RULE", entityIdExpression = "#ruleKey")
    @Transactional
    public Rule enableRule(String ruleKey, String operator) {
        Rule rule = ruleRepository.findByRuleKey(ruleKey)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + ruleKey));
        rule.setStatus(RuleStatus.ACTIVE);
        rule.setEnabled(true);
        rule.setUpdatedBy(operator);
        return ruleRepository.save(rule);
    }

    /**
     * 禁用规则
     */
    @Auditable(event = AuditEvent.RULE_DISABLE, entityType = "RULE", entityIdExpression = "#ruleKey")
    @Transactional
    public Rule disableRule(String ruleKey, String operator) {
        Rule rule = ruleRepository.findByRuleKey(ruleKey)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + ruleKey));
        rule.setEnabled(false);
        rule.setUpdatedBy(operator);
        return ruleRepository.save(rule);
    }

    /**
     * 获取规则详情
     */
    public Rule getRule(String ruleKey) {
        return ruleRepository.findByRuleKey(ruleKey)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + ruleKey));
    }

    /**
     * 列出所有规则（分页）
     */
    public Page<Rule> listRules(Pageable pageable) {
        return ruleRepository.findAll(pageable);
    }
}
