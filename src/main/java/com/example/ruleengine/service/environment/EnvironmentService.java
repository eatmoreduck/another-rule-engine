package com.example.ruleengine.service.environment;

import com.example.ruleengine.constants.EnvironmentType;
import com.example.ruleengine.domain.Environment;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.repository.EnvironmentRepository;
import com.example.ruleengine.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 环境隔离服务
 * 支持多环境: DEV, STAGING, PRODUCTION
 * 每个环境独立的规则集，支持环境间规则复制
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnvironmentService {

    private final EnvironmentRepository environmentRepository;
    private final RuleRepository ruleRepository;

    /**
     * 获取所有环境列表
     */
    public List<Environment> listEnvironments() {
        return environmentRepository.findAll();
    }

    /**
     * 获取环境详情
     */
    public Environment getEnvironment(Long id) {
        return environmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("环境不存在: " + id));
    }

    /**
     * 根据名称获取环境
     */
    public Environment getEnvironmentByName(String name) {
        return environmentRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("环境不存在: " + name));
    }

    /**
     * 创建新环境
     */
    @Transactional
    public Environment createEnvironment(String name, EnvironmentType type, String description) {
        if (environmentRepository.existsByName(name)) {
            throw new IllegalArgumentException("环境名称已存在: " + name);
        }

        Environment environment = Environment.builder()
                .name(name)
                .type(type)
                .description(description)
                .build();

        Environment saved = environmentRepository.save(environment);
        log.info("创建环境: name={}, type={}", name, type);
        return saved;
    }

    /**
     * 获取环境下的所有规则
     */
    public List<Rule> getRulesByEnvironment(Long environmentId) {
        Environment env = getEnvironment(environmentId);
        return ruleRepository.findByEnvironmentId(env.getId());
    }

    /**
     * 获取环境下的启用规则
     */
    public List<Rule> getActiveRulesByEnvironment(Long environmentId) {
        Environment env = getEnvironment(environmentId);
        return ruleRepository.findByEnvironmentIdAndEnabledTrue(env.getId());
    }

    /**
     * 克隆环境规则
     * 将源环境的所有规则复制到目标环境
     */
    @Transactional
    public com.example.ruleengine.model.dto.CloneEnvironmentResponse cloneEnvironmentRules(
            String fromEnvName, String toEnvName, boolean overwrite, String operator) {

        Environment fromEnv = getEnvironmentByName(fromEnvName);
        Environment toEnv = getEnvironmentByName(toEnvName);

        List<Rule> sourceRules = ruleRepository.findByEnvironmentId(fromEnv.getId());
        List<String> targetRuleKeys = ruleRepository.findRuleKeysByEnvironmentId(toEnv.getId());

        int clonedCount = 0;
        int skippedCount = 0;

        for (Rule sourceRule : sourceRules) {
            boolean existsInTarget = targetRuleKeys.contains(sourceRule.getRuleKey());

            if (existsInTarget && !overwrite) {
                skippedCount++;
                continue;
            }

            // 如果目标环境已存在且选择覆盖，先删除旧规则（通过 ruleKey + environmentId）
            if (existsInTarget && overwrite) {
                // 找到目标环境的同名规则并更新
                List<Rule> targetRules = ruleRepository.findByEnvironmentId(toEnv.getId());
                targetRules.stream()
                        .filter(r -> r.getRuleKey().equals(sourceRule.getRuleKey()))
                        .findFirst()
                        .ifPresent(existing -> {
                            existing.setGroovyScript(sourceRule.getGroovyScript());
                            existing.setRuleName(sourceRule.getRuleName());
                            existing.setRuleDescription(sourceRule.getRuleDescription());
                            existing.setVersion(sourceRule.getVersion());
                            existing.setUpdatedBy(operator);
                            ruleRepository.save(existing);
                        });
                clonedCount++;
            } else {
                // 创建新规则（指向目标环境）
                Rule newRule = Rule.builder()
                        .ruleKey(sourceRule.getRuleKey())
                        .ruleName(sourceRule.getRuleName())
                        .ruleDescription(sourceRule.getRuleDescription())
                        .groovyScript(sourceRule.getGroovyScript())
                        .version(sourceRule.getVersion())
                        .status(sourceRule.getStatus())
                        .createdBy(operator)
                        .enabled(sourceRule.getEnabled())
                        .environmentId(toEnv.getId())
                        .build();
                ruleRepository.save(newRule);
                clonedCount++;
            }
        }

        log.info("克隆环境规则: from={}, to={}, cloned={}, skipped={}",
                fromEnvName, toEnvName, clonedCount, skippedCount);

        return com.example.ruleengine.model.dto.CloneEnvironmentResponse.success(clonedCount, skippedCount);
    }
}
