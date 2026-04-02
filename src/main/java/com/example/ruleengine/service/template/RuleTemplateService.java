package com.example.ruleengine.service.template;

import com.example.ruleengine.domain.CustomTemplate;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.domain.RuleTemplate;
import com.example.ruleengine.model.dto.CreateCustomTemplateRequest;
import com.example.ruleengine.model.dto.InstantiateTemplateRequest;
import com.example.ruleengine.repository.CustomTemplateRepository;
import com.example.ruleengine.repository.RuleTemplateRepository;
import com.example.ruleengine.validator.GroovyScriptValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 规则模板服务
 * 提供系统预置模板管理和用户自定义模板功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleTemplateService {

    private final RuleTemplateRepository ruleTemplateRepository;
    private final CustomTemplateRepository customTemplateRepository;

    /**
     * 获取所有系统预置模板
     */
    public List<RuleTemplate> getTemplates() {
        return ruleTemplateRepository.findAll();
    }

    /**
     * 获取指定分类的模板
     */
    public List<RuleTemplate> getTemplatesByCategory(String category) {
        return ruleTemplateRepository.findByCategory(category);
    }

    /**
     * 获取模板详情
     */
    public RuleTemplate getTemplate(Long id) {
        return ruleTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + id));
    }

    /**
     * 从系统模板创建规则
     * 将模板中的占位符 {{paramName}} 替换为实际参数值
     */
    @Transactional
    public Rule createFromTemplate(Long templateId, InstantiateTemplateRequest request) {
        RuleTemplate template = getTemplate(templateId);

        // 替换模板参数
        String groovyScript = template.getGroovyTemplate();
        if (request.getParameters() != null) {
            for (Map.Entry<String, Object> entry : request.getParameters().entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                groovyScript = groovyScript.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }

        // 检查是否还有未替换的占位符
        if (groovyScript.contains("{{") && groovyScript.contains("}}")) {
            throw new IllegalArgumentException("模板参数未完全替换，请检查参数是否齐全");
        }

        // 构建规则（不直接保存，返回 Rule 对象供调用方处理）
        String ruleName = request.getRuleName() != null ? request.getRuleName() : template.getName();
        String operator = request.getOperator() != null ? request.getOperator() : "system";

        Rule rule = Rule.builder()
                .ruleKey(request.getRuleKey())
                .ruleName(ruleName)
                .ruleDescription(request.getRuleDescription() != null ? request.getRuleDescription() : template.getDescription())
                .groovyScript(groovyScript)
                .version(1)
                .createdBy(operator)
                .enabled(true)
                .build();

        log.info("从模板创建规则: templateId={}, ruleKey={}", templateId, request.getRuleKey());
        return rule;
    }

    // ========== 个人模板 (RCONF-04) ==========

    /**
     * 保存个人模板
     */
    @Transactional
    public CustomTemplate saveCustomTemplate(CreateCustomTemplateRequest request) {
        CustomTemplate template = CustomTemplate.builder()
                .name(request.getName())
                .description(request.getDescription())
                .groovyTemplate(request.getGroovyTemplate())
                .parameters(request.getParameters())
                .createdBy(request.getCreatedBy() != null ? request.getCreatedBy() : "system")
                .build();

        CustomTemplate saved = customTemplateRepository.save(template);
        log.info("保存个人模板: name={}, createdBy={}", request.getName(), request.getCreatedBy());
        return saved;
    }

    /**
     * 获取个人模板列表
     */
    public List<CustomTemplate> getCustomTemplates(String createdBy) {
        if (createdBy != null) {
            return customTemplateRepository.findByCreatedBy(createdBy);
        }
        return customTemplateRepository.findAll();
    }

    /**
     * 获取个人模板详情
     */
    public CustomTemplate getCustomTemplate(Long id) {
        return customTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("个人模板不存在: " + id));
    }

    /**
     * 从个人模板创建规则
     */
    @Transactional
    public Rule createFromCustomTemplate(Long templateId, InstantiateTemplateRequest request) {
        CustomTemplate template = getCustomTemplate(templateId);

        // 替换模板参数
        String groovyScript = template.getGroovyTemplate();
        if (request.getParameters() != null) {
            for (Map.Entry<String, Object> entry : request.getParameters().entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                groovyScript = groovyScript.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }

        String ruleName = request.getRuleName() != null ? request.getRuleName() : template.getName();
        String operator = request.getOperator() != null ? request.getOperator() : "system";

        Rule rule = Rule.builder()
                .ruleKey(request.getRuleKey())
                .ruleName(ruleName)
                .ruleDescription(request.getRuleDescription() != null ? request.getRuleDescription() : template.getDescription())
                .groovyScript(groovyScript)
                .version(1)
                .createdBy(operator)
                .enabled(true)
                .build();

        log.info("从个人模板创建规则: templateId={}, ruleKey={}", templateId, request.getRuleKey());
        return rule;
    }

    /**
     * 删除个人模板
     */
    @Transactional
    public void deleteCustomTemplate(Long id) {
        if (!customTemplateRepository.existsById(id)) {
            throw new IllegalArgumentException("个人模板不存在: " + id);
        }
        customTemplateRepository.deleteById(id);
        log.info("删除个人模板: id={}", id);
    }
}
