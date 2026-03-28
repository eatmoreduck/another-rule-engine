package com.example.ruleengine.service.version;

import com.example.ruleengine.constants.RuleStatus;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.domain.RuleVersion;
import com.example.ruleengine.model.dto.CreateVersionRequest;
import com.example.ruleengine.model.dto.VersionDiffResponse;
import com.example.ruleengine.model.dto.VersionResponse;
import com.example.ruleengine.repository.RuleRepository;
import com.example.ruleengine.repository.RuleVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 规则版本管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VersionManagementService {

    private final RuleRepository ruleRepository;
    private final RuleVersionRepository ruleVersionRepository;

    /**
     * 创建规则新版本
     * 1. 保存当前版本到 rule_versions
     * 2. 更新 rules 表的 groovy_script 和 version
     * 3. 记录变更原因和操作人
     */
    @Transactional
    public VersionResponse createVersion(String ruleKey, CreateVersionRequest request, String operator) {
        // 1. 查询规则
        Rule rule = ruleRepository.findByRuleKey(ruleKey)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + ruleKey));

        // 2. 保存当前版本到历史表
        Integer currentVersion = rule.getVersion();
        RuleVersion historyVersion = RuleVersion.builder()
                .ruleId(rule.getId())
                .ruleKey(rule.getRuleKey())
                .version(currentVersion)
                .groovyScript(rule.getGroovyScript())
                .changeReason(request.getChangeReason())
                .changedBy(operator)
                .isRollback(false)
                .build();
        ruleVersionRepository.save(historyVersion);

        // 3. 更新规则
        Integer newVersion = currentVersion + 1;
        rule.setGroovyScript(request.getGroovyScript());
        rule.setVersion(newVersion);
        rule.setUpdatedBy(operator);
        Rule updatedRule = ruleRepository.save(rule);

        log.info("创建规则新版本: ruleKey={}, version={}, operator={}", ruleKey, newVersion, operator);

        return VersionResponse.builder()
                .ruleId(updatedRule.getId())
                .ruleKey(updatedRule.getRuleKey())
                .version(updatedRule.getVersion())
                .groovyScript(updatedRule.getGroovyScript())
                .changedBy(operator)
                .build();
    }

    /**
     * 获取规则的所有历史版本
     */
    public List<VersionResponse> getVersions(String ruleKey) {
        List<RuleVersion> versions = ruleVersionRepository.findByRuleKeyOrderByVersionDesc(ruleKey);
        return versions.stream()
                .map(VersionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 获取规则的特定版本
     */
    public VersionResponse getVersion(String ruleKey, Integer version) {
        RuleVersion ruleVersion = ruleVersionRepository.findByRuleKeyAndVersion(ruleKey, version)
                .orElseThrow(() -> new IllegalArgumentException("版本不存在: ruleKey=" + ruleKey + ", version=" + version));
        return VersionResponse.fromEntity(ruleVersion);
    }

    /**
     * 比较两个版本的差异
     */
    public VersionDiffResponse compareVersions(String ruleKey, Integer version1, Integer version2) {
        RuleVersion v1 = ruleVersionRepository.findByRuleKeyAndVersion(ruleKey, version1)
                .orElseThrow(() -> new IllegalArgumentException("版本不存在: " + version1));
        RuleVersion v2 = ruleVersionRepository.findByRuleKeyAndVersion(ruleKey, version2)
                .orElseThrow(() -> new IllegalArgumentException("版本不存在: " + version2));

        // 简单的差异比较（实际项目中可以使用 Apache Commons Text 的 StringSubstitutor 或 diffutils）
        String diff = generateSimpleDiff(v1.getGroovyScript(), v2.getGroovyScript());

        return VersionDiffResponse.builder()
                .ruleKey(ruleKey)
                .version1(version1)
                .version2(version2)
                .script1(v1.getGroovyScript())
                .script2(v2.getGroovyScript())
                .diff(diff)
                .build();
    }

    /**
     * 回滚规则到指定版本
     * 1. 将当前版本保存到 rule_versions（标记为 is_rollback=true）
     * 2. 从 rule_versions 恢复目标版本的脚本
     * 3. 更新 rules 表
     * 4. 记录回滚操作
     */
    @Transactional
    public VersionResponse rollbackToVersion(String ruleKey, Integer targetVersion, String operator) {
        // 1. 查询规则和目标版本
        Rule rule = ruleRepository.findByRuleKey(ruleKey)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + ruleKey));
        RuleVersion targetVersionEntity = ruleVersionRepository.findByRuleKeyAndVersion(ruleKey, targetVersion)
                .orElseThrow(() -> new IllegalArgumentException("目标版本不存在: " + targetVersion));

        Integer currentVersion = rule.getVersion();

        // 2. 保存当前版本到历史表（标记为回滚）
        RuleVersion rollbackHistory = RuleVersion.builder()
                .ruleId(rule.getId())
                .ruleKey(rule.getRuleKey())
                .version(currentVersion)
                .groovyScript(rule.getGroovyScript())
                .changeReason("回滚到版本 " + targetVersion)
                .changedBy(operator)
                .isRollback(true)
                .rollbackFromVersion(currentVersion)
                .build();
        ruleVersionRepository.save(rollbackHistory);

        // 3. 更新规则为目标版本的内容
        Integer newVersion = currentVersion + 1;
        rule.setGroovyScript(targetVersionEntity.getGroovyScript());
        rule.setVersion(newVersion);
        rule.setUpdatedBy(operator);
        Rule updatedRule = ruleRepository.save(rule);

        log.info("回滚规则版本: ruleKey={}, from={}, to={}, newVersion={}, operator={}",
                ruleKey, currentVersion, targetVersion, newVersion, operator);

        return VersionResponse.builder()
                .ruleId(updatedRule.getId())
                .ruleKey(updatedRule.getRuleKey())
                .version(updatedRule.getVersion())
                .groovyScript(updatedRule.getGroovyScript())
                .changedBy(operator)
                .isRollback(true)
                .rollbackFromVersion(currentVersion)
                .build();
    }

    /**
     * 生成简单的差异文本
     */
    private String generateSimpleDiff(String script1, String script2) {
        if (script1.equals(script2)) {
            return "两个版本内容完全相同";
        }

        int maxLength = Math.max(script1.length(), script2.length());
        int diffIndex = -1;
        for (int i = 0; i < Math.min(script1.length(), script2.length()); i++) {
            if (script1.charAt(i) != script2.charAt(i)) {
                diffIndex = i;
                break;
            }
        }

        if (diffIndex == -1) {
            return "内容长度不同: version1=" + script1.length() + ", version2=" + script2.length();
        }

        int contextStart = Math.max(0, diffIndex - 50);
        int contextEnd = Math.min(maxLength, diffIndex + 50);

        return String.format("首次差异出现在位置 %d\n版本1: ...%s...\n版本2: ...%s...",
                diffIndex,
                script1.substring(contextStart, Math.min(script1.length(), contextEnd)),
                script2.substring(contextStart, Math.min(script2.length(), contextEnd)));
    }
}
