package com.example.ruleengine.service.lifecycle;

import com.example.ruleengine.annotation.Auditable;
import com.example.ruleengine.constants.AuditEvent;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.model.dto.CreateRuleRequest;
import com.example.ruleengine.model.dto.RuleQuery;
import com.example.ruleengine.model.dto.UpdateRuleRequest;
import com.example.ruleengine.repository.RuleRepository;
import com.example.ruleengine.service.auth.DataPermissionService;
import com.example.ruleengine.validator.RuleUsageChecker;
import com.example.ruleengine.service.version.VersionManagementService;
import com.example.ruleengine.validator.GroovyScriptValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    private final RuleUsageChecker ruleUsageChecker;
    private final DataPermissionService dataPermissionService;

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

        // 3. 更新规则（不涉及脚本变更时直接更新）
        if (request.getGroovyScript() == null || request.getGroovyScript().equals(rule.getGroovyScript())) {
            // 仅更新元数据，不创建新版本
            if (request.getRuleName() != null) {
                rule.setRuleName(request.getRuleName());
            }
            if (request.getRuleDescription() != null) {
                rule.setRuleDescription(request.getRuleDescription());
            }
            rule.setUpdatedBy(operator);

            Rule updated = ruleRepository.save(rule);
            log.info("更新规则元数据: ruleKey={}, operator={}", ruleKey, operator);
            return updated;
        }

        // 4. 脚本变更时创建新版本
        String changeReason = request.getChangeReason() != null ? request.getChangeReason() : "更新规则脚本";
        com.example.ruleengine.model.dto.CreateVersionRequest versionRequest =
            com.example.ruleengine.model.dto.CreateVersionRequest.builder()
                .groovyScript(request.getGroovyScript())
                .changeReason(changeReason)
                .build();

        versionManagementService.createVersion(ruleKey, versionRequest, operator);

        // 5. 更新规则元数据
        Rule updatedRule = ruleRepository.findByRuleKey(ruleKey)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + ruleKey));

        if (request.getRuleName() != null) {
            updatedRule.setRuleName(request.getRuleName());
        }
        if (request.getRuleDescription() != null) {
            updatedRule.setRuleDescription(request.getRuleDescription());
        }
        updatedRule.setUpdatedBy(operator);

        Rule updated = ruleRepository.save(updatedRule);
        log.info("更新规则并创建新版本: ruleKey={}, newVersion={}, operator={}", ruleKey, updated.getVersion(), operator);
        return updated;
    }

    /**
     * 删除规则（软删除）
     */
    @Auditable(event = AuditEvent.RULE_DELETE, entityType = "RULE", entityIdExpression = "#ruleKey")
    @Transactional
    public void deleteRule(String ruleKey, String operator) {
        // 1. 验证规则存在
        Rule rule = ruleRepository.findByRuleKey(ruleKey)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + ruleKey));

        // 2. 检查规则是否在使用中
        if (!ruleUsageChecker.canSafelyDelete(ruleKey)) {
            throw new IllegalStateException("规则正在使用中，不能删除: " + ruleKey);
        }

        // 3. 软删除
        rule.setDeleted(true);
        rule.setEnabled(false);
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
     * @param pageable 分页参数
     * @param showDeleted 是否包含已删除规则，默认 false（仅返回未删除）
     * @param keyword 关键词搜索（ruleKey/ruleName 模糊匹配），可为 null
     * @param enabled 按启用状态过滤，可为 null（不过滤）
     */
    public Page<Rule> listRules(Pageable pageable, boolean showDeleted, String keyword, Boolean enabled) {
        boolean isAdmin = dataPermissionService.isCurrentUserAdmin();
        boolean teamFilter = !isAdmin;
        List<Long> teamIds = isAdmin ? List.of() : dataPermissionService.getCurrentUserTeamIds();

        boolean hasFilter = keyword != null || enabled != null;
        if (hasFilter) {
            Boolean deleted = showDeleted ? null : false;
            return ruleRepository.findByConditionsWithoutDatesAndWithTeam(
                    null,
                    enabled,
                    keyword,
                    deleted,
                    teamFilter,
                    teamIds,
                    pageable
            );
        }
        if (showDeleted) {
            return ruleRepository.findAll(pageable);
        }
        return ruleRepository.findByDeletedFalseWithTeam(teamFilter, teamIds, pageable);
    }

    /**
     * 查询规则（支持多条件过滤）
     */
    public Page<Rule> queryRules(RuleQuery query, Pageable pageable) {
        log.info("查询规则: query={}", query);
        boolean isAdmin = dataPermissionService.isCurrentUserAdmin();
        boolean teamFilter = !isAdmin;
        List<Long> teamIds = isAdmin ? List.of() : dataPermissionService.getCurrentUserTeamIds();

        boolean hasDateFilter = query.getCreatedAtStart() != null || query.getCreatedAtEnd() != null
                || query.getUpdatedAtStart() != null || query.getUpdatedAtEnd() != null;
        if (hasDateFilter) {
            return ruleRepository.findByConditionsWithTeam(
                    query.getCreatedBy(),
                    query.getEnabled(),
                    query.getKeyword(),
                    false,
                    query.getCreatedAtStart(),
                    query.getCreatedAtEnd(),
                    query.getUpdatedAtStart(),
                    query.getUpdatedAtEnd(),
                    teamFilter,
                    teamIds,
                    pageable
            );
        }
        return ruleRepository.findByConditionsWithoutDatesAndWithTeam(
                query.getCreatedBy(),
                query.getEnabled(),
                query.getKeyword(),
                false,
                teamFilter,
                teamIds,
                pageable
        );
    }
}
