package com.example.ruleengine.controller;

import com.example.ruleengine.model.DecisionRequest;
import com.example.ruleengine.model.DecisionResponse;
import com.example.ruleengine.service.RuleExecutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DecisionController 集成测试
 * <p>
 * 测试覆盖:
 * 1. 正常请求处理
 * 2. 请求验证
 * 3. 响应格式验证
 * 4. 错误处理
 * 5. 边界条件
 */
@DisplayName("DecisionController 测试")
@WebMvcTest(DecisionController.class)
class DecisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RuleExecutionService ruleExecutionService;

    // ==================== 正常请求测试 ====================

    @Nested
    @DisplayName("正常请求测试")
    class NormalRequestTests {

        @Test
        @DisplayName("POST /api/v1/decide 应返回 PASS 决策")
        void shouldReturnPassDecision() throws Exception {
            // Arrange
            DecisionResponse response = DecisionResponse.builder()
                .decision("PASS")
                .reason("规则执行完成")
                .executionTimeMs(15)
                .build();

            when(ruleExecutionService.executeDecision(any(DecisionRequest.class)))
                .thenReturn(response);

            // Act & Assert
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"rule-001\",\"script\":\"return true\",\"features\":{},\"requiredFeatures\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("PASS"))
                .andExpect(jsonPath("$.reason").value("规则执行完成"))
                .andExpect(jsonPath("$.executionTimeMs").value(15));
        }

        @Test
        @DisplayName("POST /api/v1/decide 应返回 REJECT 决策")
        void shouldReturnRejectDecision() throws Exception {
            // Arrange
            DecisionResponse response = DecisionResponse.builder()
                .decision("REJECT")
                .reason("金额超过阈值")
                .executionTimeMs(10)
                .build();

            when(ruleExecutionService.executeDecision(any(DecisionRequest.class)))
                .thenReturn(response);

            // Act & Assert
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"rule-002\",\"script\":\"return false\",\"features\":{},\"requiredFeatures\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REJECT"))
                .andExpect(jsonPath("$.reason").value("金额超过阈值"));
        }

        @Test
        @DisplayName("带特征的请求应正确处理")
        void shouldHandleRequestWithFeatures() throws Exception {
            // Arrange
            DecisionResponse response = DecisionResponse.builder()
                .decision("PASS")
                .reason("验证通过")
                .executionTimeMs(20)
                .build();

            when(ruleExecutionService.executeDecision(any(DecisionRequest.class)))
                .thenReturn(response);

            // Act & Assert
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"rule-003\",\"script\":\"return amount < 10000\",\"features\":{\"amount\":5000,\"userId\":\"user123\"},\"requiredFeatures\":[\"amount\",\"userId\"],\"timeoutMs\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("PASS"));
        }

        @Test
        @DisplayName("带执行上下文的响应应正确返回")
        void shouldReturnResponseWithContext() throws Exception {
            // Arrange
            DecisionResponse response = new DecisionResponse();
            response.setDecision("PASS");
            response.setReason("规则执行完成");
            response.setExecutionTimeMs(25);

            Map<String, Object> context = new HashMap<>();
            context.put("user_age", 25);
            context.put("risk_score", 0.2);
            response.setExecutionContext(context);

            when(ruleExecutionService.executeDecision(any(DecisionRequest.class)))
                .thenReturn(response);

            // Act & Assert
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"rule-004\",\"script\":\"return true\",\"features\":{},\"requiredFeatures\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionContext.user_age").value(25))
                .andExpect(jsonPath("$.executionContext.risk_score").value(0.2));
        }
    }

    // ==================== 请求验证测试 ====================

    @Nested
    @DisplayName("请求验证测试")
    class RequestValidationTests {

        @Test
        @DisplayName("缺少 ruleId 的请求应返回错误")
        void shouldRejectMissingRuleId() throws Exception {
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"script\":\"return true\",\"features\":{}}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("空请求体应返回错误")
        void shouldRejectEmptyBody() throws Exception {
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("无效 JSON 应返回错误")
        void shouldRejectInvalidJson() throws Exception {
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{invalid json}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("缺少 Content-Type 应返回错误")
        void shouldRejectMissingContentType() throws Exception {
            mockMvc.perform(post("/api/v1/decide")
                    .content("{\"ruleId\":\"rule-001\",\"script\":\"return true\"}"))
                .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("错误的 Content-Type 应返回错误")
        void shouldRejectWrongContentType() throws Exception {
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{\"ruleId\":\"rule-001\",\"script\":\"return true\"}"))
                .andExpect(status().isUnsupportedMediaType());
        }
    }

    // ==================== 错误处理测试 ====================

    @Nested
    @DisplayName("错误处理测试")
    class ErrorHandlingTests {

        @Test
        @DisplayName("服务抛出异常应返回 500 错误")
        void shouldReturn500OnServiceException() throws Exception {
            // Arrange
            when(ruleExecutionService.executeDecision(any(DecisionRequest.class)))
                .thenThrow(new RuntimeException("服务内部错误"));

            // Act & Assert
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"error-rule\",\"script\":\"return true\",\"features\":{}}"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.decision").value("REJECT"))
                .andExpect(jsonPath("$.reason").value("决策请求失败: 服务内部错误"));
        }

        @Test
        @DisplayName("超时响应应正确返回")
        void shouldReturnTimeoutResponse() throws Exception {
            // Arrange
            DecisionResponse response = DecisionResponse.builder()
                .decision("REJECT")
                .reason("规则执行超时")
                .executionTimeMs(50)
                .timeout(true)
                .build();

            when(ruleExecutionService.executeDecision(any(DecisionRequest.class)))
                .thenReturn(response);

            // Act & Assert
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"timeout-rule\",\"script\":\"sleep(1000)\",\"features\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REJECT"))
                .andExpect(jsonPath("$.reason").value("规则执行超时"))
                .andExpect(jsonPath("$.timeout").value(true));
        }
    }

    // ==================== 健康检查测试 ====================

    @Nested
    @DisplayName("健康检查测试")
    class HealthCheckTests {

        @Test
        @DisplayName("GET /api/v1/health 应返回 OK")
        void shouldReturnOkForHealthCheck() throws Exception {
            mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
        }
    }

    // ==================== HTTP 方法测试 ====================

    @Nested
    @DisplayName("HTTP 方法测试")
    class HttpMethodTests {

        @Test
        @DisplayName("GET 请求到 /api/v1/decide 应返回 405")
        void shouldRejectGetRequestToDecide() throws Exception {
            mockMvc.perform(get("/api/v1/decide"))
                .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("POST 请求应正确处理")
        void shouldAcceptPostRequest() throws Exception {
            // Arrange
            DecisionResponse response = DecisionResponse.builder()
                .decision("PASS")
                .reason("成功")
                .executionTimeMs(5)
                .build();

            when(ruleExecutionService.executeDecision(any(DecisionRequest.class)))
                .thenReturn(response);

            // Act & Assert
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"rule-001\",\"script\":\"return true\",\"features\":{}}"))
                .andExpect(status().isOk());
        }
    }

    // ==================== 响应格式测试 ====================

    @Nested
    @DisplayName("响应格式测试")
    class ResponseFormatTests {

        @Test
        @DisplayName("响应应包含所有必需字段")
        void shouldContainAllRequiredFields() throws Exception {
            // Arrange
            DecisionResponse response = DecisionResponse.builder()
                .decision("PASS")
                .reason("规则执行完成")
                .executionTimeMs(10)
                .build();

            when(ruleExecutionService.executeDecision(any(DecisionRequest.class)))
                .thenReturn(response);

            // Act & Assert
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"rule-001\",\"script\":\"return true\",\"features\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").exists())
                .andExpect(jsonPath("$.reason").exists())
                .andExpect(jsonPath("$.executionTimeMs").exists());
        }

        @Test
        @DisplayName("响应 Content-Type 应为 application/json")
        void shouldReturnJsonContentType() throws Exception {
            // Arrange
            DecisionResponse response = DecisionResponse.builder()
                .decision("PASS")
                .reason("成功")
                .executionTimeMs(5)
                .build();

            when(ruleExecutionService.executeDecision(any(DecisionRequest.class)))
                .thenReturn(response);

            // Act & Assert
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"rule-001\",\"script\":\"return true\",\"features\":{}}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }
    }

    // ==================== 边界条件测试 ====================

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("长脚本内容应正常处理")
        void shouldHandleLongScript() throws Exception {
            // Arrange
            DecisionResponse response = DecisionResponse.builder()
                .decision("PASS")
                .reason("成功")
                .executionTimeMs(15)
                .build();

            when(ruleExecutionService.executeDecision(any(DecisionRequest.class)))
                .thenReturn(response);

            // 构建较长的脚本
            StringBuilder longScript = new StringBuilder("def result = 0; ");
            for (int i = 0; i < 100; i++) {
                longScript.append("result += ").append(i).append("; ");
            }
            longScript.append("return result > 0");

            // Act & Assert
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"long-script-rule\",\"script\":\"" + longScript + "\",\"features\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("PASS"));
        }

        @Test
        @DisplayName("大量特征数据应正常处理")
        void shouldHandleLargeFeaturesMap() throws Exception {
            // Arrange
            DecisionResponse response = DecisionResponse.builder()
                .decision("PASS")
                .reason("成功")
                .executionTimeMs(20)
                .build();

            when(ruleExecutionService.executeDecision(any(DecisionRequest.class)))
                .thenReturn(response);

            // 构建大量特征
            StringBuilder featuresJson = new StringBuilder("{");
            for (int i = 0; i < 50; i++) {
                if (i > 0) featuresJson.append(",");
                featuresJson.append("\"feature_").append(i).append("\":").append(i);
            }
            featuresJson.append("}");

            // Act & Assert
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"large-features-rule\",\"script\":\"return true\",\"features\":" + featuresJson + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("PASS"));
        }

        @Test
        @DisplayName("特殊字符在脚本中应正常处理")
        void shouldHandleSpecialCharactersInScript() throws Exception {
            // Arrange
            DecisionResponse response = DecisionResponse.builder()
                .decision("PASS")
                .reason("成功")
                .executionTimeMs(5)
                .build();

            when(ruleExecutionService.executeDecision(any(DecisionRequest.class)))
                .thenReturn(response);

            // Act & Assert - 脚本包含中文和特殊符号
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"special-chars-rule\",\"script\":\"// 金额检查\\nreturn amount > 100\",\"features\":{}}"))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Unicode 字符应正常处理")
        void shouldHandleUnicodeCharacters() throws Exception {
            // Arrange
            DecisionResponse response = DecisionResponse.builder()
                .decision("PASS")
                .reason("验证通过 \uD83D\uDC4D")
                .executionTimeMs(5)
                .build();

            when(ruleExecutionService.executeDecision(any(DecisionRequest.class)))
                .thenReturn(response);

            // Act & Assert
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"unicode-rule\",\"script\":\"return true\",\"features\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("验证通过 \uD83D\uDC4D"));
        }
    }

    // ==================== 服务调用验证测试 ====================

    @Nested
    @DisplayName("服务调用验证测试")
    class ServiceInvocationTests {

        @Test
        @DisplayName("应正确调用 RuleExecutionService")
        void shouldCallRuleExecutionService() throws Exception {
            // Arrange
            DecisionResponse response = DecisionResponse.builder()
                .decision("PASS")
                .reason("成功")
                .executionTimeMs(5)
                .build();

            when(ruleExecutionService.executeDecision(any(DecisionRequest.class)))
                .thenReturn(response);

            // Act
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"rule-001\",\"script\":\"return true\",\"features\":{}}"))
                .andExpect(status().isOk());

            // Assert
            verify(ruleExecutionService, times(1)).executeDecision(any(DecisionRequest.class));
        }

        @Test
        @DisplayName("请求参数应正确传递")
        void shouldPassCorrectParameters() throws Exception {
            // Arrange
            DecisionResponse response = DecisionResponse.builder()
                .decision("PASS")
                .reason("成功")
                .executionTimeMs(5)
                .build();

            when(ruleExecutionService.executeDecision(any(DecisionRequest.class)))
                .thenReturn(response);

            // Act
            mockMvc.perform(post("/api/v1/decide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"test-rule-123\",\"script\":\"return amount > 100\",\"features\":{\"amount\":500},\"requiredFeatures\":[\"amount\"],\"timeoutMs\":100}"))
                .andExpect(status().isOk());

            // Assert
            verify(ruleExecutionService).executeDecision(argThat(req ->
                "test-rule-123".equals(req.getRuleId()) &&
                "return amount > 100".equals(req.getScript()) &&
                req.getFeatures() != null &&
                req.getFeatures().containsKey("amount")
            ));
        }
    }
}
