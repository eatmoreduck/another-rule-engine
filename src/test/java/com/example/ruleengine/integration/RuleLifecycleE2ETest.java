package com.example.ruleengine.integration;

import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.model.dto.CreateRuleRequest;
import com.example.ruleengine.model.dto.UpdateRuleRequest;
import com.example.ruleengine.validator.RuleUsageChecker;
import com.example.ruleengine.service.lifecycle.RuleLifecycleService;
import com.example.ruleengine.service.version.VersionManagementService;
import com.example.ruleengine.validator.GroovyScriptValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 规则生命周期端到端测试
 * 测试完整的规则生命周期流程：
 * 1. 创建规则 → enabled=true
 * 2. 启用规则 → enabled=true
 * 3. 更新规则 → 创建版本 2
 * 4. 禁用规则 → enabled=false
 * 5. 删除规则 → deleted=true, enabled=false
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("规则生命周期端到端测试")
class RuleLifecycleE2ETest {

    @Mock
    private com.example.ruleengine.repository.RuleRepository ruleRepository;

    @Mock
    private GroovyScriptValidator scriptValidator;

    @Mock
    private VersionManagementService versionManagementService;

    @Mock
    private RuleUsageChecker ruleUsageChecker;

    @InjectMocks
    private RuleLifecycleService ruleLifecycleService;

    private CreateRuleRequest createRequest;
    private UpdateRuleRequest updateRequest;
    private Rule testRule;

    @BeforeEach
    void setUp() {
        // 初始化创建请求
        createRequest = CreateRuleRequest.builder()
                .ruleKey("e2e_test_rule")
                .ruleName("端到端测试规则")
                .ruleDescription("用于端到端测试的规则")
                .groovyScript("return features.amount > 1000")
                .build();

        // 初始化更新请求
        updateRequest = UpdateRuleRequest.builder()
                .ruleName("更新后的端到端测试规则")
                .groovyScript("return features.amount > 2000")
                .changeReason("提高阈值")
                .build();

        // 初始化测试规则
        testRule = Rule.builder()
                .id(1L)
                .ruleKey("e2e_test_rule")
                .ruleName("端到端测试规则")
                .ruleDescription("用于端到端测试的规则")
                .groovyScript("return features.amount > 1000")
                .version(1)
                .createdBy("test_user")
                .enabled(true)
                .build();

        // 配置默认 Mock 行为
        when(ruleRepository.existsByRuleKey(any())).thenReturn(false);
        when(scriptValidator.validate(any())).thenReturn(GroovyScriptValidator.ValidationResult.success());
        when(ruleUsageChecker.canSafelyDelete(any())).thenReturn(true);
    }

    @Test
    @DisplayName("完整规则生命周期流程测试")
    void testCompleteRuleLifecycle() {
        String operator = "e2e_test_user";

        // 步骤 1: 创建规则 → enabled=true
        when(ruleRepository.save(any(Rule.class))).thenReturn(testRule);
        Rule createdRule = ruleLifecycleService.createRule(createRequest, operator);

        assertNotNull(createdRule);
        assertEquals("e2e_test_rule", createdRule.getRuleKey());
        assertEquals(1, createdRule.getVersion());
        assertTrue(createdRule.getEnabled());

        // 步骤 2: 启用规则 → enabled=true
        Rule activeRule = Rule.builder()
                .id(1L)
                .ruleKey("e2e_test_rule")
                .enabled(true)
                .build();

        when(ruleRepository.findByRuleKey("e2e_test_rule")).thenReturn(java.util.Optional.of(testRule));
        when(ruleRepository.save(any(Rule.class))).thenReturn(activeRule);

        Rule enabledRule = ruleLifecycleService.enableRule("e2e_test_rule", operator);

        assertNotNull(enabledRule);
        assertTrue(enabledRule.getEnabled());

        // 步骤 3: 更新规则 → 创建版本 2
        Rule updatedRule = Rule.builder()
                .id(1L)
                .ruleKey("e2e_test_rule")
                .ruleName("更新后的端到端测试规则")
                .groovyScript("return features.amount > 2000")
                .version(2)
                .enabled(true)
                .build();

        when(ruleRepository.findByRuleKey("e2e_test_rule")).thenReturn(java.util.Optional.of(activeRule));
        when(versionManagementService.createVersion(any(), any(), any())).thenAnswer(invocation -> {
            activeRule.setVersion(2);
            activeRule.setGroovyScript("return features.amount > 2000");
            return null;
        });
        when(ruleRepository.save(any(Rule.class))).thenReturn(updatedRule);

        Rule result = ruleLifecycleService.updateRule("e2e_test_rule", updateRequest, operator);

        assertNotNull(result);
        assertEquals("更新后的端到端测试规则", result.getRuleName());

        // 步骤 4: 禁用规则 → enabled=false
        Rule disabledRule = Rule.builder()
                .id(1L)
                .ruleKey("e2e_test_rule")
                .enabled(false)
                .build();

        when(ruleRepository.findByRuleKey("e2e_test_rule")).thenReturn(java.util.Optional.of(updatedRule));
        when(ruleRepository.save(any(Rule.class))).thenReturn(disabledRule);

        Rule result2 = ruleLifecycleService.disableRule("e2e_test_rule", operator);

        assertNotNull(result2);
        assertFalse(result2.getEnabled());

        // 步骤 5: 删除规则 → deleted=true, enabled=false
        Rule deletedRule = Rule.builder()
                .id(1L)
                .ruleKey("e2e_test_rule")
                .deleted(true)
                .enabled(false)
                .build();

        when(ruleRepository.findByRuleKey("e2e_test_rule")).thenReturn(java.util.Optional.of(disabledRule));
        when(ruleRepository.save(any(Rule.class))).thenReturn(deletedRule);

        ruleLifecycleService.deleteRule("e2e_test_rule", operator);

        Rule finalRule = ruleLifecycleService.getRule("e2e_test_rule");
        assertTrue(finalRule.getDeleted());
        assertFalse(finalRule.getEnabled());
    }

    @Test
    @DisplayName("规则状态转换验证")
    void testRuleStateTransitions() {
        String operator = "e2e_test_user_2";

        // 创建规则
        when(ruleRepository.save(any(Rule.class))).thenReturn(testRule);
        Rule rule = ruleLifecycleService.createRule(createRequest, operator);
        assertTrue(rule.getEnabled());

        // 启用规则
        Rule activeRule = Rule.builder()
                .id(1L)
                .ruleKey("e2e_test_rule")
                .enabled(true)
                .build();

        when(ruleRepository.findByRuleKey("e2e_test_rule")).thenReturn(java.util.Optional.of(testRule));
        when(ruleRepository.save(any(Rule.class))).thenReturn(activeRule);

        Rule result = ruleLifecycleService.enableRule("e2e_test_rule", operator);
        assertTrue(result.getEnabled());

        // 禁用规则
        Rule disabledRule = Rule.builder()
                .id(1L)
                .ruleKey("e2e_test_rule")
                .enabled(false)
                .build();

        when(ruleRepository.findByRuleKey("e2e_test_rule")).thenReturn(java.util.Optional.of(activeRule));
        when(ruleRepository.save(any(Rule.class))).thenReturn(disabledRule);

        Rule result2 = ruleLifecycleService.disableRule("e2e_test_rule", operator);
        assertFalse(result2.getEnabled());

        // 删除规则 → deleted=true
        Rule deletedRule = Rule.builder()
                .id(1L)
                .ruleKey("e2e_test_rule")
                .deleted(true)
                .enabled(false)
                .build();

        when(ruleRepository.findByRuleKey("e2e_test_rule")).thenReturn(java.util.Optional.of(disabledRule));
        when(ruleRepository.save(any(Rule.class))).thenReturn(deletedRule);

        ruleLifecycleService.deleteRule("e2e_test_rule", operator);

        Rule finalRule = ruleLifecycleService.getRule("e2e_test_rule");
        assertTrue(finalRule.getDeleted());
        assertFalse(finalRule.getEnabled());
    }

    @Test
    @DisplayName("规则版本管理验证")
    void testRuleVersionManagement() {
        String operator = "e2e_test_user_3";

        // 创建规则（版本1）
        when(ruleRepository.save(any(Rule.class))).thenReturn(testRule);
        Rule rule1 = ruleLifecycleService.createRule(createRequest, operator);
        assertEquals(1, rule1.getVersion());

        // 更新规则（版本2）
        Rule rule2 = Rule.builder()
                .id(1L)
                .ruleKey("e2e_test_rule")
                .version(2)
                .groovyScript("return features.amount > 2000")
                .build();

        when(ruleRepository.findByRuleKey("e2e_test_rule")).thenReturn(java.util.Optional.of(testRule));
        when(versionManagementService.createVersion(any(), any(), any())).thenAnswer(invocation -> {
            testRule.setVersion(2);
            testRule.setGroovyScript("return features.amount > 2000");
            return null;
        });
        when(ruleRepository.save(any(Rule.class))).thenReturn(rule2);

        Rule result = ruleLifecycleService.updateRule("e2e_test_rule", updateRequest, operator);
        assertEquals(2, result.getVersion());

        // 再次更新（版本3）
        Rule rule3 = Rule.builder()
                .id(1L)
                .ruleKey("e2e_test_rule")
                .version(3)
                .groovyScript("return features.amount > 3000")
                .build();

        UpdateRuleRequest anotherUpdate = UpdateRuleRequest.builder()
                .groovyScript("return features.amount > 3000")
                .changeReason("再次提高阈值")
                .build();

        when(ruleRepository.findByRuleKey("e2e_test_rule")).thenReturn(java.util.Optional.of(rule2));
        when(versionManagementService.createVersion(any(), any(), any())).thenAnswer(invocation -> {
            rule2.setVersion(3);
            rule2.setGroovyScript("return features.amount > 3000");
            return null;
        });
        when(ruleRepository.save(any(Rule.class))).thenReturn(rule3);

        Rule result2 = ruleLifecycleService.updateRule("e2e_test_rule", anotherUpdate, operator);
        assertEquals(3, result2.getVersion());
    }
}
