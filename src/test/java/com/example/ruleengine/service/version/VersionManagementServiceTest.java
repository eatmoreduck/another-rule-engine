package com.example.ruleengine.service.version;

import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.domain.RuleVersion;
import com.example.ruleengine.model.dto.CreateVersionRequest;
import com.example.ruleengine.repository.RuleRepository;
import com.example.ruleengine.repository.RuleVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VersionManagementService 集成测试
 * 使用 H2 内存数据库替代 Testcontainers PostgreSQL
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("VersionManagementService 集成测试")
class VersionManagementServiceTest {

    @Autowired
    private VersionManagementService versionManagementService;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private RuleVersionRepository ruleVersionRepository;

    private Rule testRule;

    @BeforeEach
    void setUp() {
        ruleVersionRepository.deleteAllInBatch();
        ruleRepository.deleteAllInBatch();

        testRule = Rule.builder()
                .ruleKey("test-version-rule")
                .ruleName("版本测试规则")
                .groovyScript("def version1() { return 'v1' }")
                .version(1)
                .createdBy("test-user")
                .enabled(true)
                .build();
        testRule = ruleRepository.save(testRule);
    }

    @Test
    @DisplayName("应该成功创建新版本")
    void shouldCreateNewVersion() {
        CreateVersionRequest request = CreateVersionRequest.builder()
                .groovyScript("def version2() { return 'v2' }")
                .changeReason("更新逻辑")
                .build();

        var response = versionManagementService.createVersion(
                testRule.getRuleKey(), request, "test-operator");

        assertThat(response.getVersion()).isEqualTo(2);
        assertThat(response.getGroovyScript()).contains("v2");

        // 验证规则表已更新
        Rule updatedRule = ruleRepository.findByRuleKey(testRule.getRuleKey()).orElseThrow();
        assertThat(updatedRule.getVersion()).isEqualTo(2);
        assertThat(updatedRule.getGroovyScript()).contains("v2");

        // 验证历史表有记录
        List<RuleVersion> versions = ruleVersionRepository.findByRuleKeyOrderByVersionDesc(testRule.getRuleKey());
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).getVersion()).isEqualTo(1);
        assertThat(versions.get(0).getGroovyScript()).contains("v1");
    }

    @Test
    @DisplayName("应该成功获取版本列表")
    void shouldGetVersions() {
        // 创建多个版本
        CreateVersionRequest request1 = CreateVersionRequest.builder()
                .groovyScript("def version2() { return 'v2' }")
                .changeReason("第一次更新")
                .build();
        versionManagementService.createVersion(testRule.getRuleKey(), request1, "operator1");

        CreateVersionRequest request2 = CreateVersionRequest.builder()
                .groovyScript("def version3() { return 'v3' }")
                .changeReason("第二次更新")
                .build();
        versionManagementService.createVersion(testRule.getRuleKey(), request2, "operator2");

        // 获取版本列表
        var versions = versionManagementService.getVersions(testRule.getRuleKey());
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).getVersion()).isEqualTo(2);
        assertThat(versions.get(1).getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该成功回滚到历史版本")
    void shouldRollbackToVersion() {
        // 先创建版本2
        CreateVersionRequest request = CreateVersionRequest.builder()
                .groovyScript("def version2() { return 'v2' }")
                .changeReason("创建版本2")
                .build();
        versionManagementService.createVersion(testRule.getRuleKey(), request, "operator1");

        // 回滚到版本1
        var response = versionManagementService.rollbackToVersion(
                testRule.getRuleKey(), 1, "operator2");

        assertThat(response.getVersion()).isEqualTo(3);
        assertThat(response.getGroovyScript()).contains("v1");
        assertThat(response.getIsRollback()).isTrue();
        assertThat(response.getRollbackFromVersion()).isEqualTo(2);

        // 验证规则表已回滚
        Rule rule = ruleRepository.findByRuleKey(testRule.getRuleKey()).orElseThrow();
        assertThat(rule.getVersion()).isEqualTo(3);
        assertThat(rule.getGroovyScript()).contains("v1");
    }

    @Test
    @DisplayName("回滚后创建新版本应该继续递增")
    void shouldContinueIncrementingAfterRollback() {
        // 创建版本2
        CreateVersionRequest request1 = CreateVersionRequest.builder()
                .groovyScript("def version2() { return 'v2' }")
                .build();
        versionManagementService.createVersion(testRule.getRuleKey(), request1, "operator1");

        // 回滚到版本1
        versionManagementService.rollbackToVersion(testRule.getRuleKey(), 1, "operator2");

        // 创建新版本
        CreateVersionRequest request2 = CreateVersionRequest.builder()
                .groovyScript("def version4() { return 'v4' }")
                .build();
        var response = versionManagementService.createVersion(testRule.getRuleKey(), request2, "operator3");

        assertThat(response.getVersion()).isEqualTo(4);

        // 验证版本历史（按 version desc 排序）
        // version=3: createVersion 保存的（回滚后的当前版本）
        // version=2: rollbackToVersion 保存的（isRollback=true）
        // version=1: 第一次 createVersion 保存的
        List<RuleVersion> versions = ruleVersionRepository.findByRuleKeyOrderByVersionDesc(testRule.getRuleKey());
        assertThat(versions).hasSize(3);
        assertThat(versions.get(0).getVersion()).isEqualTo(3);
        assertThat(versions.get(0).getIsRollback()).isFalse();
        assertThat(versions.get(1).getVersion()).isEqualTo(2);
        assertThat(versions.get(1).getIsRollback()).isTrue();
        assertThat(versions.get(2).getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该成功比较版本差异")
    void shouldCompareVersions() {
        // 创建版本2 -> 历史表保存 version=1
        CreateVersionRequest request1 = CreateVersionRequest.builder()
                .groovyScript("def version2() { return 'v2' }")
                .build();
        versionManagementService.createVersion(testRule.getRuleKey(), request1, "operator");

        // 创建版本3 -> 历史表保存 version=2
        CreateVersionRequest request2 = CreateVersionRequest.builder()
                .groovyScript("def version3() { return 'v3' }")
                .build();
        versionManagementService.createVersion(testRule.getRuleKey(), request2, "operator");

        // 现在历史表中有 version=1 和 version=2，可以比较
        var diff = versionManagementService.compareVersions(testRule.getRuleKey(), 1, 2);

        assertThat(diff.getRuleKey()).isEqualTo(testRule.getRuleKey());
        assertThat(diff.getVersion1()).isEqualTo(1);
        assertThat(diff.getVersion2()).isEqualTo(2);
        assertThat(diff.getScript1()).contains("v1");
        assertThat(diff.getScript2()).contains("v2");
        assertThat(diff.getDiff()).isNotEmpty();
    }
}
