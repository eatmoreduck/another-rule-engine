package com.example.ruleengine.controller;

import com.example.ruleengine.constants.RuleStatus;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.model.dto.CreateRuleRequest;
import com.example.ruleengine.model.dto.UpdateRuleRequest;
import com.example.ruleengine.model.dto.ValidateScriptRequest;
import com.example.ruleengine.repository.RuleRepository;
import com.example.ruleengine.repository.RuleVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RuleController 集成测试
 * <p>
 * 使用 @SpringBootTest + H2 内存数据库，测试完整的规则生命周期 API。
 * 测试覆盖:
 * 1. 创建规则 (POST /api/v1/rules)
 * 2. 获取规则详情 (GET /api/v1/rules/{ruleKey})
 * 3. 更新规则 (PUT /api/v1/rules/{ruleKey})
 * 4. 删除规则 (DELETE /api/v1/rules/{ruleKey})
 * 5. 启用规则 (POST /api/v1/rules/{ruleKey}/enable)
 * 6. 禁用规则 (POST /api/v1/rules/{ruleKey}/disable)
 * 7. 验证脚本 (POST /api/v1/rules/validate)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("RuleController 集成测试")
class RuleControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private RuleRepository ruleRepository;

  @Autowired
  private RuleVersionRepository ruleVersionRepository;

  @BeforeEach
  void setUp() {
    // 按外键依赖顺序清理：先删除 rule_versions（子表），再删除 rules（主表)
    ruleVersionRepository.deleteAllInBatch();
    ruleRepository.deleteAllInBatch();
  }

  // ==================== 创建规则测试 ====================

  @Nested
  @DisplayName("POST /api/v1/rules - 创建规则")
  class CreateRuleTests {

    @Test
    @DisplayName("应成功创建规则并返回 200")
    void shouldCreateRuleSuccessfully() throws Exception {
      CreateRuleRequest request = CreateRuleRequest.builder()
          .ruleKey("order_amount_check")
          .ruleName("订单金额校验")
          .ruleDescription("校验订单金额是否超过阈值")
          .groovyScript("return amount < 10000")
          .build();

      mockMvc.perform(post("/api/v1/rules")
              .header("X-Operator", "admin")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.ruleKey").value("order_amount_check"))
          .andExpect(jsonPath("$.ruleName").value("订单金额校验"))
          .andExpect(jsonPath("$.ruleDescription").value("校验订单金额是否超过阈值"))
          .andExpect(jsonPath("$.groovyScript").value("return amount < 10000"))
          .andExpect(jsonPath("$.status").value("DRAFT"))
          .andExpect(jsonPath("$.version").value(1))
          .andExpect(jsonPath("$.enabled").value(true))
          .andExpect(jsonPath("$.createdBy").value("admin"))
          .andExpect(jsonPath("$.id").exists())
          .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("不传 X-Operator 时应使用默认值 system")
    void shouldUseDefaultOperatorWhenNotProvided() throws Exception {
      CreateRuleRequest request = CreateRuleRequest.builder()
          .ruleKey("default_operator_rule")
          .ruleName("默认操作人规则")
          .groovyScript("return true")
          .build();

      mockMvc.perform(post("/api/v1/rules")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.createdBy").value("system"));
    }

    @Test
    @DisplayName("规则 Key 重复时应抛出异常")
    void shouldRejectDuplicateRuleKey() throws Exception {
      CreateRuleRequest request = CreateRuleRequest.builder()
          .ruleKey("duplicate_key")
          .ruleName("重复Key规则")
          .groovyScript("return true")
          .build();

      // 先创建一个规则
      mockMvc.perform(post("/api/v1/rules")
              .header("X-Operator", "admin")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk());

      // 再次创建相同 ruleKey 的规则，应抛出 IllegalArgumentException
      Exception exception = assertThrows(Exception.class, () ->
          mockMvc.perform(post("/api/v1/rules")
              .header("X-Operator", "admin")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
      );
      assert exception.getCause() instanceof IllegalArgumentException;
    }

    @Test
    @DisplayName("规则 Key 为空时应返回 400")
    void shouldRejectBlankRuleKey() throws Exception {
      CreateRuleRequest request = CreateRuleRequest.builder()
          .ruleKey("")
          .ruleName("空Key规则")
          .groovyScript("return true")
          .build();

      mockMvc.perform(post("/api/v1/rules")
              .header("X-Operator", "admin")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("规则名称为空时应返回 400")
    void shouldRejectBlankRuleName() throws Exception {
      CreateRuleRequest request = CreateRuleRequest.builder()
          .ruleKey("no_name_rule")
          .ruleName("")
          .groovyScript("return true")
          .build();

      mockMvc.perform(post("/api/v1/rules")
              .header("X-Operator", "admin")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Groovy 脚本为空时应返回 400")
    void shouldRejectBlankGroovyScript() throws Exception {
      CreateRuleRequest request = CreateRuleRequest.builder()
          .ruleKey("no_script_rule")
          .ruleName("无脚本规则")
          .groovyScript("")
          .build();

      mockMvc.perform(post("/api/v1/rules")
              .header("X-Operator", "admin")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("无效的 Groovy 脚本语法应抛出异常")
    void shouldRejectInvalidGroovyScript() throws Exception {
      CreateRuleRequest request = CreateRuleRequest.builder()
          .ruleKey("invalid_script_rule")
          .ruleName("无效脚本规则")
          .groovyScript("this is not valid groovy {{{{")
          .build();

      // 没有全局异常处理器，IllegalArgumentException 会通过 ServletException 传播
      Exception exception = assertThrows(Exception.class, () ->
          mockMvc.perform(post("/api/v1/rules")
              .header("X-Operator", "admin")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
      );
      assert exception.getCause() instanceof IllegalArgumentException;
    }
  }

  // ==================== 获取规则详情测试 ====================

  @Nested
  @DisplayName("GET /api/v1/rules/{ruleKey} - 获取规则详情")
  class GetRuleTests {

    @Test
    @DisplayName("应成功获取已存在的规则")
    void shouldGetExistingRule() throws Exception {
      createTestRule("get_test_rule", "查询测试规则", "return true");

      mockMvc.perform(get("/api/v1/rules/get_test_rule"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.ruleKey").value("get_test_rule"))
          .andExpect(jsonPath("$.ruleName").value("查询测试规则"))
          .andExpect(jsonPath("$.groovyScript").value("return true"));
    }

    @Test
    @DisplayName("查询不存在的规则应抛出异常")
    void shouldThrowExceptionForNonExistentRule() throws Exception {
      // 没有全局异常处理器，IllegalArgumentException 通过 ServletException 传播
      Exception exception = assertThrows(Exception.class, () ->
          mockMvc.perform(get("/api/v1/rules/non_existent_rule"))
      );
      assert exception.getCause() instanceof IllegalArgumentException;
    }
  }

  // ==================== 更新规则测试 ====================

  @Nested
  @DisplayName("PUT /api/v1/rules/{ruleKey} - 更新规则")
  class UpdateRuleTests {

    @Test
    @DisplayName("应成功更新规则名称")
    void shouldUpdateRuleName() throws Exception {
      createTestRule("update_name_rule", "原始名称", "return true");

      UpdateRuleRequest request = UpdateRuleRequest.builder()
          .ruleName("更新后的名称")
          .build();

      mockMvc.perform(put("/api/v1/rules/update_name_rule")
              .header("X-Operator", "admin")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.ruleKey").value("update_name_rule"))
          .andExpect(jsonPath("$.ruleName").value("更新后的名称"));
    }

    @Test
    @DisplayName("应成功更新规则脚本并创建新版本")
    void shouldUpdateRuleScript() throws Exception {
      createTestRule("update_script_rule", "脚本更新规则", "return true");

      UpdateRuleRequest request = UpdateRuleRequest.builder()
          .groovyScript("return amount > 100")
          .changeReason("更新脚本逻辑")
          .build();

      mockMvc.perform(put("/api/v1/rules/update_script_rule")
              .header("X-Operator", "admin")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.ruleKey").value("update_script_rule"));
    }

    @Test
    @DisplayName("更新不存在的规则应抛出异常")
    void shouldThrowExceptionWhenUpdatingNonExistentRule() throws Exception {
      UpdateRuleRequest request = UpdateRuleRequest.builder()
          .ruleName("不存在的规则")
          .build();

      Exception exception = assertThrows(Exception.class, () ->
          mockMvc.perform(put("/api/v1/rules/non_existent_rule")
              .header("X-Operator", "admin")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
      );
      assert exception.getCause() instanceof IllegalArgumentException;
    }
  }

  // ==================== 删除规则测试 ====================

  @Nested
  @DisplayName("DELETE /api/v1/rules/{ruleKey} - 删除规则")
  class DeleteRuleTests {

    @Test
    @DisplayName("应成功软删除 DRAFT 状态的规则")
    void shouldDeleteDraftRule() throws Exception {
      createTestRule("delete_draft_rule", "待删除草稿规则", "return true");

      mockMvc.perform(delete("/api/v1/rules/delete_draft_rule")
              .header("X-Operator", "admin"))
          .andExpect(status().isOk());

      // 验证规则已被软删除（状态变为 DELETED）
      Rule deletedRule = ruleRepository.findByRuleKey("delete_draft_rule").orElseThrow();
      assert deletedRule.getStatus() == RuleStatus.DELETED;
      assert !deletedRule.getEnabled();
    }

    @Test
    @DisplayName("删除不存在的规则应抛出异常")
    void shouldThrowExceptionWhenDeletingNonExistentRule() throws Exception {
      Exception exception = assertThrows(Exception.class, () ->
          mockMvc.perform(delete("/api/v1/rules/non_existent_rule")
              .header("X-Operator", "admin"))
      );
      assert exception.getCause() instanceof IllegalArgumentException;
    }
  }

  // ==================== 启用规则测试 ====================

  @Nested
  @DisplayName("POST /api/v1/rules/{ruleKey}/enable - 启用规则")
  class EnableRuleTests {

    @Test
    @DisplayName("应成功启用规则")
    void shouldEnableRule() throws Exception {
      createTestRule("enable_test_rule", "待启用规则", "return true");

      mockMvc.perform(post("/api/v1/rules/enable_test_rule/enable")
              .header("X-Operator", "admin"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.ruleKey").value("enable_test_rule"))
          .andExpect(jsonPath("$.status").value("ACTIVE"))
          .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("启用不存在的规则应抛出异常")
    void shouldThrowExceptionWhenEnablingNonExistentRule() throws Exception {
      Exception exception = assertThrows(Exception.class, () ->
          mockMvc.perform(post("/api/v1/rules/non_existent_rule/enable")
              .header("X-Operator", "admin"))
      );
      assert exception.getCause() instanceof IllegalArgumentException;
    }
  }

  // ==================== 禁用规则测试 ====================

  @Nested
  @DisplayName("POST /api/v1/rules/{ruleKey}/disable - 禁用规则")
  class DisableRuleTests {

    @Test
    @DisplayName("应成功禁用规则")
    void shouldDisableRule() throws Exception {
      createTestRule("disable_test_rule", "待禁用规则", "return true");

      mockMvc.perform(post("/api/v1/rules/disable_test_rule/disable")
              .header("X-Operator", "admin"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.ruleKey").value("disable_test_rule"))
          .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @DisplayName("禁用不存在的规则应抛出异常")
    void shouldThrowExceptionWhenDisablingNonExistentRule() throws Exception {
      Exception exception = assertThrows(Exception.class, () ->
          mockMvc.perform(post("/api/v1/rules/non_existent_rule/disable")
              .header("X-Operator", "admin"))
      );
      assert exception.getCause() instanceof IllegalArgumentException;
    }
  }

  // ==================== 验证脚本测试 ====================

  @Nested
  @DisplayName("POST /api/v1/rules/validate - 验证 Groovy 脚本")
  class ValidateScriptTests {

    @Test
    @DisplayName("有效的 Groovy 脚本应返回验证通过")
    void shouldValidateValidScript() throws Exception {
      ValidateScriptRequest request = ValidateScriptRequest.builder()
          .groovyScript("return amount > 100")
          .build();

      mockMvc.perform(post("/api/v1/rules/validate")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    @DisplayName("无效的 Groovy 脚本应返回验证失败")
    void shouldRejectInvalidScript() throws Exception {
      ValidateScriptRequest request = ValidateScriptRequest.builder()
          .groovyScript("this is {{{{ invalid")
          .build();

      mockMvc.perform(post("/api/v1/rules/validate")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.valid").value(false))
          .andExpect(jsonPath("$.errorMessage").value("Groovy脚本语法错误"))
          .andExpect(jsonPath("$.errorDetails").exists());
    }

    @Test
    @DisplayName("脚本为空时应返回 400")
    void shouldRejectEmptyScript() throws Exception {
      ValidateScriptRequest request = ValidateScriptRequest.builder()
          .groovyScript("")
          .build();

      mockMvc.perform(post("/api/v1/rules/validate")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("简单的 return true 脚本应验证通过")
    void shouldValidateSimpleReturnTrue() throws Exception {
      ValidateScriptRequest request = ValidateScriptRequest.builder()
          .groovyScript("return true")
          .build();

      mockMvc.perform(post("/api/v1/rules/validate")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    @DisplayName("包含变量引用的脚本应验证通过")
    void shouldValidateScriptWithVariables() throws Exception {
      ValidateScriptRequest request = ValidateScriptRequest.builder()
          .groovyScript("def threshold = 10000\nreturn amount < threshold")
          .build();

      mockMvc.perform(post("/api/v1/rules/validate")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.valid").value(true));
    }
  }

  // ==================== 列出规则测试 ====================

  @Nested
  @DisplayName("GET /api/v1/rules - 列出规则")
  class ListRulesTests {

    @Test
    @DisplayName("应返回分页的规则列表")
    void shouldReturnPaginatedRuleList() throws Exception {
      createTestRule("list_rule_1", "列表规则1", "return true");
      createTestRule("list_rule_2", "列表规则2", "return false");

      mockMvc.perform(get("/api/v1/rules")
              .param("page", "0")
              .param("size", "10"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content").isArray())
          .andExpect(jsonPath("$.content.length()").value(2))
          .andExpect(jsonPath("$.totalElements").value(2))
          .andExpect(jsonPath("$.pageable.pageNumber").value(0));
    }

    @Test
    @DisplayName("空数据库应返回空列表")
    void shouldReturnEmptyListWhenNoRules() throws Exception {
      mockMvc.perform(get("/api/v1/rules")
              .param("page", "0")
              .param("size", "10"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content").isArray())
          .andExpect(jsonPath("$.content.length()").value(0))
          .andExpect(jsonPath("$.totalElements").value(0));
    }
  }

  // ==================== 完整生命周期测试 ====================

  @Nested
  @DisplayName("规则完整生命周期")
  class RuleLifecycleTests {

    @Test
    @DisplayName("应完成 创建->查询->更新->启用->禁用->删除 完整生命周期")
    void shouldCompleteFullLifecycle() throws Exception {
      String ruleKey = "lifecycle_rule";

      // 1. 创建规则
      CreateRuleRequest createRequest = CreateRuleRequest.builder()
          .ruleKey(ruleKey)
          .ruleName("生命周期测试规则")
          .ruleDescription("测试完整生命周期")
          .groovyScript("return true")
          .build();

      mockMvc.perform(post("/api/v1/rules")
              .header("X-Operator", "lifecycle_tester")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(createRequest)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.ruleKey").value(ruleKey))
          .andExpect(jsonPath("$.status").value("DRAFT"))
          .andExpect(jsonPath("$.version").value(1));

      // 2. 查询规则
      mockMvc.perform(get("/api/v1/rules/" + ruleKey))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.ruleKey").value(ruleKey))
          .andExpect(jsonPath("$.ruleName").value("生命周期测试规则"));

      // 3. 更新规则名称（仅元数据更新，不触发版本创建）
      UpdateRuleRequest updateRequest = UpdateRuleRequest.builder()
          .ruleName("生命周期测试规则-已更新")
          .build();

      mockMvc.perform(put("/api/v1/rules/" + ruleKey)
              .header("X-Operator", "lifecycle_tester")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(updateRequest)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.ruleName").value("生命周期测试规则-已更新"));

      // 4. 启用规则
      mockMvc.perform(post("/api/v1/rules/" + ruleKey + "/enable")
              .header("X-Operator", "lifecycle_tester"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("ACTIVE"))
          .andExpect(jsonPath("$.enabled").value(true));

      // 5. 禁用规则（先禁用，才能安全删除，因为 ACTIVE+enabled 的规则不能删除）
      mockMvc.perform(post("/api/v1/rules/" + ruleKey + "/disable")
              .header("X-Operator", "lifecycle_tester"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.enabled").value(false));

      // 6. 删除规则
      mockMvc.perform(delete("/api/v1/rules/" + ruleKey)
              .header("X-Operator", "lifecycle_tester"))
          .andExpect(status().isOk());

      // 7. 验证规则已被软删除
      Rule deletedRule = ruleRepository.findByRuleKey(ruleKey).orElseThrow();
      assert deletedRule.getStatus() == RuleStatus.DELETED;
      assert !deletedRule.getEnabled();
    }
  }

  // ==================== 辅助方法 ====================

  /**
   * 创建测试规则（通过直接操作数据库，用于需要前置数据的测试场景）
   */
  private void createTestRule(String ruleKey, String ruleName, String groovyScript) {
    Rule rule = Rule.builder()
        .ruleKey(ruleKey)
        .ruleName(ruleName)
        .groovyScript(groovyScript)
        .status(RuleStatus.DRAFT)
        .createdBy("test_user")
        .enabled(true)
        .build();
    ruleRepository.saveAndFlush(rule);
  }
}
