package com.example.ruleengine.service.importexport;

import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.domain.RuleVersion;
import com.example.ruleengine.model.dto.ImportRulesResponse;
import com.example.ruleengine.model.dto.RuleExportData;
import com.example.ruleengine.repository.RuleRepository;
import com.example.ruleengine.repository.RuleVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则导入导出服务
 * 导出: 规则 + 版本 + 配置 -> JSON
 * 导入: JSON -> 规则 + 版本 + 配置
 * 支持批量导入导出
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "rule-engine.features.import-export.enabled", havingValue = "true")
public class RuleImportExportService {

    private final RuleRepository ruleRepository;
    private final RuleVersionRepository ruleVersionRepository;

    /**
     * 导出所有规则
     */
    public RuleExportData exportAllRules(String operator) {
        List<Rule> allRules = ruleRepository.findAll();

        List<RuleExportData.RuleRecord> records = new ArrayList<>();
        for (Rule rule : allRules) {
            List<RuleVersion> versions = ruleVersionRepository.findByRuleKeyOrderByVersionDesc(rule.getRuleKey());
            records.add(RuleExportData.RuleRecord.builder()
                    .rule(rule)
                    .versions(versions)
                    .build());
        }

        log.info("导出所有规则: count={}", records.size());

        return RuleExportData.builder()
                .formatVersion("1.0")
                .exportedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .exportedBy(operator)
                .rules(records)
                .build();
    }

    /**
     * 导出单条规则
     */
    public RuleExportData exportRule(String ruleKey, String operator) {
        Rule rule = ruleRepository.findByRuleKey(ruleKey)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在: " + ruleKey));

        List<RuleVersion> versions = ruleVersionRepository.findByRuleKeyOrderByVersionDesc(ruleKey);

        List<RuleExportData.RuleRecord> records = List.of(
                RuleExportData.RuleRecord.builder()
                        .rule(rule)
                        .versions(versions)
                        .build()
        );

        log.info("导出规则: ruleKey={}", ruleKey);

        return RuleExportData.builder()
                .formatVersion("1.0")
                .exportedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .exportedBy(operator)
                .rules(records)
                .build();
    }

    /**
     * 批量导出规则
     */
    public RuleExportData exportRules(List<String> ruleKeys, String operator) {
        List<Rule> rules = ruleRepository.findByRuleKeyIn(ruleKeys);

        List<RuleExportData.RuleRecord> records = new ArrayList<>();
        for (Rule rule : rules) {
            List<RuleVersion> versions = ruleVersionRepository.findByRuleKeyOrderByVersionDesc(rule.getRuleKey());
            records.add(RuleExportData.RuleRecord.builder()
                    .rule(rule)
                    .versions(versions)
                    .build());
        }

        log.info("批量导出规则: count={}", records.size());

        return RuleExportData.builder()
                .formatVersion("1.0")
                .exportedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .exportedBy(operator)
                .rules(records)
                .build();
    }

    /**
     * 导入规则
     */
    @Transactional
    public ImportRulesResponse importRules(RuleExportData exportData, String operator) {
        if (exportData.getRules() == null || exportData.getRules().isEmpty()) {
            return ImportRulesResponse.success(0, 0, 0, List.of());
        }

        int importedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        List<String> failures = new ArrayList<>();

        for (RuleExportData.RuleRecord record : exportData.getRules()) {
            try {
                Rule ruleData = record.getRule();

                if (ruleRepository.existsByRuleKey(ruleData.getRuleKey())) {
                    // 规则已存在，跳过
                    skippedCount++;
                    continue;
                }

                // 创建规则（清除 ID，使用新的 ID）
                Rule newRule = Rule.builder()
                        .ruleKey(ruleData.getRuleKey())
                        .ruleName(ruleData.getRuleName())
                        .ruleDescription(ruleData.getRuleDescription())
                        .groovyScript(ruleData.getGroovyScript())
                        .version(ruleData.getVersion())
                        .deleted(false)
                        .createdBy(operator)
                        .enabled(ruleData.getEnabled())
                        .environmentId(ruleData.getEnvironmentId())
                        .build();
                ruleRepository.save(newRule);

                // 导入版本历史
                if (record.getVersions() != null) {
                    for (RuleVersion versionData : record.getVersions()) {
                        RuleVersion newVersion = RuleVersion.builder()
                                .ruleId(newRule.getId())
                                .ruleKey(versionData.getRuleKey())
                                .version(versionData.getVersion())
                                .groovyScript(versionData.getGroovyScript())
                                .changeReason(versionData.getChangeReason())
                                .changedBy(versionData.getChangedBy())
                                .isRollback(versionData.getIsRollback())
                                .rollbackFromVersion(versionData.getRollbackFromVersion())
                                .build();
                        ruleVersionRepository.save(newVersion);
                    }
                }

                importedCount++;
            } catch (Exception e) {
                failedCount++;
                failures.add(String.format("规则 %s 导入失败: %s",
                        record.getRule() != null ? record.getRule().getRuleKey() : "unknown",
                        e.getMessage()));
                log.error("导入规则失败", e);
            }
        }

        log.info("导入规则完成: imported={}, skipped={}, failed={}", importedCount, skippedCount, failedCount);
        return ImportRulesResponse.success(importedCount, skippedCount, failedCount, failures);
    }
}
