package com.example.ruleengine.service.lifecycle;

import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.model.dto.CreateRuleRequest;
import com.example.ruleengine.model.dto.RuleQuery;
import com.example.ruleengine.model.dto.UpdateRuleRequest;
import com.example.ruleengine.repository.RuleRepository;
import com.example.ruleengine.validator.RuleUsageChecker;
import com.example.ruleengine.service.version.VersionManagementService;
import com.example.ruleengine.validator.GroovyScriptValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RuleLifecycleService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RuleLifecycleServiceTest {

  @Mock
  private RuleRepository ruleRepository;

  @Mock
  private GroovyScriptValidator scriptValidator;

  @Mock
  private VersionManagementService versionManagementService;

  @Mock
  private RuleUsageChecker ruleUsageChecker;

  @InjectMocks
  private RuleLifecycleService ruleLifecycleService;

  private Rule testRule;
  private CreateRuleRequest createRequest;
  private UpdateRuleRequest updateRequest;

  @BeforeEach
  void setUp() {
    // 初始化测试规则
    testRule = Rule.builder()
        .id(1L)
        .ruleKey("test_rule")
        .ruleName("测试规则")
        .ruleDescription("测试规则描述")
        .groovyScript("return true")
        .version(1)
        .createdBy("test_user")
        .enabled(true)
        .build();

    // 初始化创建请求
    createRequest = CreateRuleRequest.builder()
        .ruleKey("test_rule")
        .ruleName("测试规则")
        .ruleDescription("测试规则描述")
        .groovyScript("return true")
        .build();

    // 初始化更新请求
    updateRequest = UpdateRuleRequest.builder()
        .ruleName("更新后的规则名称")
        .groovyScript("return false")
        .changeReason("更新脚本")
        .build();
  }

  // ========== 创建规则测试 ==========

  @Test
  @DisplayName("创建规则 - 正常创建")
  void testCreateRule_Success() {
    // Given
    when(ruleRepository.existsByRuleKey(anyString())).thenReturn(false);
    when(scriptValidator.validate(anyString())).thenReturn(GroovyScriptValidator.ValidationResult.success());
    when(ruleRepository.save(any(Rule.class))).thenReturn(testRule);

    // When
    Rule result = ruleLifecycleService.createRule(createRequest, "test_user");

    // Then
    assertNotNull(result);
    assertEquals("test_rule", result.getRuleKey());
    assertTrue(result.getEnabled());
    assertEquals(1, result.getVersion());
    verify(ruleRepository, times(1)).save(any(Rule.class));
  }

  @Test
  @DisplayName("创建规则 - ruleKey已存在时抛出异常")
  void testCreateRule_DuplicateKey() {
    // Given
    when(ruleRepository.existsByRuleKey(anyString())).thenReturn(true);

    // When & Then
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> ruleLifecycleService.createRule(createRequest, "test_user")
    );

    assertEquals("规则Key已存在: test_rule", exception.getMessage());
    verify(ruleRepository, never()).save(any(Rule.class));
  }

  @Test
  @DisplayName("创建规则 - Groovy脚本语法错误时抛出异常")
  void testCreateRule_InvalidScript() {
    // Given
    when(ruleRepository.existsByRuleKey(anyString())).thenReturn(false);
    when(scriptValidator.validate(anyString()))
        .thenReturn(GroovyScriptValidator.ValidationResult.error("语法错误"));

    // When & Then
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> ruleLifecycleService.createRule(createRequest, "test_user")
    );

    assertTrue(exception.getMessage().contains("Groovy脚本语法错误"));
    verify(ruleRepository, never()).save(any(Rule.class));
  }

  @Test
  @DisplayName("创建规则 - 应正确设置初始版本、状态和创建人")
  void testCreateRule_SetsCorrectInitialState() {
    // Given
    when(ruleRepository.existsByRuleKey("new_rule")).thenReturn(false);
    when(scriptValidator.validate(anyString())).thenReturn(GroovyScriptValidator.ValidationResult.success());
    when(ruleRepository.save(any(Rule.class))).thenAnswer(invocation -> invocation.getArgument(0));

    CreateRuleRequest newRequest = CreateRuleRequest.builder()
        .ruleKey("new_rule")
        .ruleName("新规则")
        .ruleDescription("描述")
        .groovyScript("return 1")
        .build();

    // When
    ruleLifecycleService.createRule(newRequest, "creator");

    // Then
    verify(ruleRepository, times(1)).save(argThat(rule ->
        "new_rule".equals(rule.getRuleKey())
            && "新规则".equals(rule.getRuleName())
            && rule.getVersion() == 1
            && rule.getEnabled()
            && "creator".equals(rule.getCreatedBy())
    ));
  }

  // ========== 更新规则测试 ==========

  @Test
  @DisplayName("更新规则 - 带脚本变更时创建新版本")
  void testUpdateRule_WithScriptChange() {
    // Given
    when(ruleRepository.findByRuleKey(anyString())).thenReturn(Optional.of(testRule));
    when(scriptValidator.validate(anyString())).thenReturn(GroovyScriptValidator.ValidationResult.success());
    when(versionManagementService.createVersion(anyString(), any(), anyString()))
        .thenAnswer(invocation -> {
          // 模拟版本创建后更新规则
          testRule.setVersion(2);
          testRule.setGroovyScript("return false");
          return null;
        });
    when(ruleRepository.save(any(Rule.class))).thenReturn(testRule);

    // When
    Rule result = ruleLifecycleService.updateRule("test_rule", updateRequest, "test_user");

    // Then
    assertNotNull(result);
    verify(versionManagementService, times(1)).createVersion(anyString(), any(), anyString());
    verify(ruleRepository, times(1)).save(any(Rule.class));
  }

  @Test
  @DisplayName("更新规则 - 仅更新元数据，不创建新版本")
  void testUpdateRule_OnlyMetadata() {
    // Given
    UpdateRuleRequest metadataOnlyRequest = UpdateRuleRequest.builder()
        .ruleName("新名称")
        .build();

    when(ruleRepository.findByRuleKey(anyString())).thenReturn(Optional.of(testRule));
    when(ruleRepository.save(any(Rule.class))).thenReturn(testRule);

    // When
    Rule result = ruleLifecycleService.updateRule("test_rule", metadataOnlyRequest, "test_user");

    // Then
    assertNotNull(result);
    verify(versionManagementService, never()).createVersion(anyString(), any(), anyString());
    verify(ruleRepository, times(1)).save(any(Rule.class));
  }

  @Test
  @DisplayName("更新规则 - 规则不存在时抛出异常")
  void testUpdateRule_RuleNotFound() {
    // Given
    when(ruleRepository.findByRuleKey("non_existent")).thenReturn(Optional.empty());

    // When & Then
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> ruleLifecycleService.updateRule("non_existent", updateRequest, "test_user")
    );

    assertTrue(exception.getMessage().contains("规则不存在"));
    verify(ruleRepository, never()).save(any(Rule.class));
    verify(versionManagementService, never()).createVersion(anyString(), any(), anyString());
  }

  @Test
  @DisplayName("更新规则 - 脚本语法错误时抛出异常")
  void testUpdateRule_InvalidScript() {
    // Given
    when(ruleRepository.findByRuleKey("test_rule")).thenReturn(Optional.of(testRule));
    when(scriptValidator.validate("return false"))
        .thenReturn(GroovyScriptValidator.ValidationResult.error("unexpected token"));

    // When & Then
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> ruleLifecycleService.updateRule("test_rule", updateRequest, "test_user")
    );

    assertTrue(exception.getMessage().contains("Groovy脚本语法错误"));
    verify(ruleRepository, never()).save(any(Rule.class));
    verify(versionManagementService, never()).createVersion(anyString(), any(), anyString());
  }

  @Test
  @DisplayName("更新规则 - 脚本内容相同时不创建新版本，仅更新元数据")
  void testUpdateRule_SameScriptNoNewVersion() {
    // Given
    UpdateRuleRequest sameScriptRequest = UpdateRuleRequest.builder()
        .groovyScript("return true")
        .ruleName("更新名称")
        .ruleDescription("更新描述")
        .build();

    when(ruleRepository.findByRuleKey("test_rule")).thenReturn(Optional.of(testRule));
    // 脚本不为null时，源码会先验证脚本语法
    when(scriptValidator.validate("return true"))
        .thenReturn(GroovyScriptValidator.ValidationResult.success());
    when(ruleRepository.save(any(Rule.class))).thenReturn(testRule);

    // When
    Rule result = ruleLifecycleService.updateRule("test_rule", sameScriptRequest, "test_user");

    // Then
    assertNotNull(result);
    verify(scriptValidator, times(1)).validate("return true");
    verify(versionManagementService, never()).createVersion(anyString(), any(), anyString());
    verify(ruleRepository, times(1)).save(any(Rule.class));
  }

  @Test
  @DisplayName("更新规则 - 脚本为null时不验证脚本，仅更新元数据")
  void testUpdateRule_NullScriptNoValidation() {
    // Given
    UpdateRuleRequest nullScriptRequest = UpdateRuleRequest.builder()
        .ruleName("新规则名称")
        .ruleDescription("新描述")
        .build();

    when(ruleRepository.findByRuleKey("test_rule")).thenReturn(Optional.of(testRule));
    when(ruleRepository.save(any(Rule.class))).thenReturn(testRule);

    // When
    Rule result = ruleLifecycleService.updateRule("test_rule", nullScriptRequest, "test_user");

    // Then
    assertNotNull(result);
    verify(scriptValidator, never()).validate(anyString());
    verify(versionManagementService, never()).createVersion(anyString(), any(), anyString());
    verify(ruleRepository, times(1)).save(any(Rule.class));
  }

  @Test
  @DisplayName("更新规则 - 脚本变更但未指定changeReason时使用默认原因")
  void testUpdateRule_ScriptChangeWithDefaultReason() {
    // Given
    UpdateRuleRequest noReasonRequest = UpdateRuleRequest.builder()
        .groovyScript("return false")
        .build();

    when(ruleRepository.findByRuleKey("test_rule")).thenReturn(Optional.of(testRule));
    when(scriptValidator.validate("return false"))
        .thenReturn(GroovyScriptValidator.ValidationResult.success());
    when(versionManagementService.createVersion(eq("test_rule"), any(), eq("test_user")))
        .thenAnswer(invocation -> {
          testRule.setVersion(2);
          testRule.setGroovyScript("return false");
          return null;
        });
    when(ruleRepository.save(any(Rule.class))).thenReturn(testRule);

    // When
    Rule result = ruleLifecycleService.updateRule("test_rule", noReasonRequest, "test_user");

    // Then
    assertNotNull(result);
    verify(versionManagementService, times(1)).createVersion(eq("test_rule"), any(), eq("test_user"));
  }

  @Test
  @DisplayName("更新规则 - 仅更新ruleDescription字段")
  void testUpdateRule_OnlyDescription() {
    // Given
    UpdateRuleRequest descOnlyRequest = UpdateRuleRequest.builder()
        .ruleDescription("新的描述内容")
        .build();

    when(ruleRepository.findByRuleKey("test_rule")).thenReturn(Optional.of(testRule));
    when(ruleRepository.save(any(Rule.class))).thenReturn(testRule);

    // When
    Rule result = ruleLifecycleService.updateRule("test_rule", descOnlyRequest, "test_user");

    // Then
    assertNotNull(result);
    verify(versionManagementService, never()).createVersion(anyString(), any(), anyString());
    verify(ruleRepository, times(1)).save(any(Rule.class));
  }

  // ========== 删除规则测试 ==========

  @Test
  @DisplayName("删除规则 - 正常软删除")
  void testDeleteRule_Success() {
    // Given
    when(ruleRepository.findByRuleKey(anyString())).thenReturn(Optional.of(testRule));
    when(ruleUsageChecker.canSafelyDelete(anyString())).thenReturn(true);
    when(ruleRepository.save(any(Rule.class))).thenReturn(testRule);

    // When
    ruleLifecycleService.deleteRule("test_rule", "test_user");

    // Then
    verify(ruleRepository, times(1)).save(any(Rule.class));
  }

  @Test
  @DisplayName("删除规则 - 规则不存在时抛出异常")
  void testDeleteRule_RuleNotFound() {
    // Given
    when(ruleRepository.findByRuleKey("non_existent")).thenReturn(Optional.empty());

    // When & Then
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> ruleLifecycleService.deleteRule("non_existent", "test_user")
    );

    assertTrue(exception.getMessage().contains("规则不存在"));
    verify(ruleRepository, never()).save(any(Rule.class));
  }

  @Test
  @DisplayName("删除规则 - 规则使用中时抛出IllegalStateException")
  void testDeleteRule_RuleInUse() {
    // Given
    when(ruleRepository.findByRuleKey(anyString())).thenReturn(Optional.of(testRule));
    when(ruleUsageChecker.canSafelyDelete(anyString())).thenReturn(false);

    // When & Then
    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> ruleLifecycleService.deleteRule("test_rule", "test_user")
    );

    assertTrue(exception.getMessage().contains("规则正在使用中"));
    verify(ruleRepository, never()).save(any(Rule.class));
  }

  @Test
  @DisplayName("删除规则 - 软删除应设置deleted为true且enabled为false")
  void testDeleteRule_SoftDeleteSetsCorrectStatus() {
    // Given
    when(ruleRepository.findByRuleKey("test_rule")).thenReturn(Optional.of(testRule));
    when(ruleUsageChecker.canSafelyDelete("test_rule")).thenReturn(true);
    when(ruleRepository.save(any(Rule.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // When
    ruleLifecycleService.deleteRule("test_rule", "test_user");

    // Then
    verify(ruleRepository, times(1)).save(argThat(rule ->
        rule.getDeleted()
            && !rule.getEnabled()
            && "test_user".equals(rule.getUpdatedBy())
    ));
  }

  // ========== 启用/禁用规则测试 ==========

  @Test
  @DisplayName("启用规则 - 正常启用")
  void testEnableRule_Success() {
    // Given
    when(ruleRepository.findByRuleKey(anyString())).thenReturn(Optional.of(testRule));
    when(ruleRepository.save(any(Rule.class))).thenReturn(testRule);

    // When
    Rule result = ruleLifecycleService.enableRule("test_rule", "test_user");

    // Then
    assertNotNull(result);
    verify(ruleRepository, times(1)).save(any(Rule.class));
  }

  @Test
  @DisplayName("启用规则 - 应设置enabled为true")
  void testEnableRule_SetsEnabledTrue() {
    // Given
    testRule.setEnabled(false);
    when(ruleRepository.findByRuleKey("test_rule")).thenReturn(Optional.of(testRule));
    when(ruleRepository.save(any(Rule.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // When
    ruleLifecycleService.enableRule("test_rule", "admin");

    // Then
    verify(ruleRepository, times(1)).save(argThat(rule ->
        rule.getEnabled()
            && "admin".equals(rule.getUpdatedBy())
    ));
  }

  @Test
  @DisplayName("启用规则 - 规则不存在时抛出异常")
  void testEnableRule_RuleNotFound() {
    // Given
    when(ruleRepository.findByRuleKey("non_existent")).thenReturn(Optional.empty());

    // When & Then
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> ruleLifecycleService.enableRule("non_existent", "test_user")
    );

    assertTrue(exception.getMessage().contains("规则不存在"));
    verify(ruleRepository, never()).save(any(Rule.class));
  }

  @Test
  @DisplayName("禁用规则 - 正常禁用")
  void testDisableRule_Success() {
    // Given
    when(ruleRepository.findByRuleKey(anyString())).thenReturn(Optional.of(testRule));
    when(ruleRepository.save(any(Rule.class))).thenReturn(testRule);

    // When
    Rule result = ruleLifecycleService.disableRule("test_rule", "test_user");

    // Then
    assertNotNull(result);
    verify(ruleRepository, times(1)).save(any(Rule.class));
  }

  @Test
  @DisplayName("禁用规则 - 应设置enabled为false")
  void testDisableRule_SetsEnabledFalse() {
    // Given
    testRule.setEnabled(true);
    when(ruleRepository.findByRuleKey("test_rule")).thenReturn(Optional.of(testRule));
    when(ruleRepository.save(any(Rule.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // When
    ruleLifecycleService.disableRule("test_rule", "admin");

    // Then
    verify(ruleRepository, times(1)).save(argThat(rule ->
        !rule.getEnabled()
            && "admin".equals(rule.getUpdatedBy())
    ));
  }

  @Test
  @DisplayName("禁用规则 - 规则不存在时抛出异常")
  void testDisableRule_RuleNotFound() {
    // Given
    when(ruleRepository.findByRuleKey("non_existent")).thenReturn(Optional.empty());

    // When & Then
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> ruleLifecycleService.disableRule("non_existent", "test_user")
    );

    assertTrue(exception.getMessage().contains("规则不存在"));
    verify(ruleRepository, never()).save(any(Rule.class));
  }

  // ========== 获取规则详情测试 ==========

  @Test
  @DisplayName("获取规则详情 - 正常获取")
  void testGetRule_Success() {
    // Given
    when(ruleRepository.findByRuleKey(anyString())).thenReturn(Optional.of(testRule));

    // When
    Rule result = ruleLifecycleService.getRule("test_rule");

    // Then
    assertNotNull(result);
    assertEquals("test_rule", result.getRuleKey());
    assertEquals("测试规则", result.getRuleName());
    assertTrue(result.getEnabled());
  }

  @Test
  @DisplayName("获取规则详情 - 规则不存在时抛出异常")
  void testGetRule_NotFound() {
    // Given
    when(ruleRepository.findByRuleKey(anyString())).thenReturn(Optional.empty());

    // When & Then
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> ruleLifecycleService.getRule("non_existent")
    );

    assertTrue(exception.getMessage().contains("规则不存在"));
  }

  // ========== 列出规则列表测试 ==========

  @Test
  @DisplayName("列出规则列表 - 返回分页结果")
  void testListRules_Success() {
    // Given
    Pageable pageable = PageRequest.of(0, 20);
    List<Rule> rules = Arrays.asList(testRule);
    Page<Rule> rulePage = new PageImpl<>(rules, pageable, 1);

    when(ruleRepository.findByDeletedFalse(pageable)).thenReturn(rulePage);

    // When
    Page<Rule> result = ruleLifecycleService.listRules(pageable, false, null, null);

    // Then
    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
    assertEquals("test_rule", result.getContent().get(0).getRuleKey());
  }

  @Test
  @DisplayName("列出规则列表 - 无数据时返回空分页")
  void testListRules_EmptyPage() {
    // Given
    Pageable pageable = PageRequest.of(0, 20);
    Page<Rule> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

    when(ruleRepository.findByDeletedFalse(pageable)).thenReturn(emptyPage);

    // When
    Page<Rule> result = ruleLifecycleService.listRules(pageable, false, null, null);

    // Then
    assertNotNull(result);
    assertEquals(0, result.getTotalElements());
    assertTrue(result.getContent().isEmpty());
  }

  // ========== 查询规则测试 ==========

  @Test
  @DisplayName("查询规则 - 按启用状态和创建人过滤")
  void testQueryRules_WithEnabledAndCreator() {
    // Given
    RuleQuery query = RuleQuery.builder()
        .enabled(true)
        .createdBy("test_user")
        .build();

    Pageable pageable = PageRequest.of(0, 20);
    List<Rule> rules = Arrays.asList(testRule);
    Page<Rule> rulePage = new PageImpl<>(rules, pageable, 1);

    when(ruleRepository.findByConditions(
        eq("test_user"),
        eq(true),
        eq(null),
        eq(false),
        eq(null),
        eq(null),
        eq(null),
        eq(null),
        eq(pageable)
    )).thenReturn(rulePage);

    // When
    Page<Rule> result = ruleLifecycleService.queryRules(query, pageable);

    // Then
    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
  }

  @Test
  @DisplayName("查询规则 - 使用空条件查询返回所有规则")
  void testQueryRules_EmptyQuery() {
    // Given
    RuleQuery emptyQuery = RuleQuery.builder().build();
    Pageable pageable = PageRequest.of(0, 10);
    List<Rule> rules = Arrays.asList(testRule);
    Page<Rule> rulePage = new PageImpl<>(rules, pageable, 1);

    when(ruleRepository.findByConditions(
        eq(null), eq(null), eq(null), eq(false),
        eq(null), eq(null), eq(null), eq(null),
        eq(pageable)
    )).thenReturn(rulePage);

    // When
    Page<Rule> result = ruleLifecycleService.queryRules(emptyQuery, pageable);

    // Then
    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
  }
}
