package com.example.ruleengine.controller;

import com.example.ruleengine.model.DecisionResponse;
import com.example.ruleengine.service.async.AsyncRuleExecutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AsyncDecisionController 测试
 *
 * 测试覆盖：
 * 1. POST /api/v1/decide/async - 提交异步决策请求
 * 2. GET /api/v1/decide/async/{requestId} - 查询结果
 * 3. 请求验证
 * 4. 错误处理
 */
@DisplayName("AsyncDecisionController 测试")
@WebMvcTest(AsyncDecisionController.class)
class AsyncDecisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AsyncRuleExecutionService asyncRuleExecutionService;

    // ==================== 提交异步请求测试 ====================

    @Nested
    @DisplayName("提交异步决策请求测试")
    class SubmitAsyncTests {

        @Test
        @DisplayName("POST /api/v1/decide/async 应返回 202 和 requestId")
        void shouldReturnAcceptedWithRequestId() throws Exception {
            when(asyncRuleExecutionService.submitAsync(any()))
                .thenReturn("test-request-id-001");

            mockMvc.perform(post("/api/v1/decide/async")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"rule-001\",\"script\":\"return true\",\"features\":{}}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").value("test-request-id-001"))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        }

        @Test
        @DisplayName("应正确调用 submitAsync 方法")
        void shouldCallSubmitAsync() throws Exception {
            when(asyncRuleExecutionService.submitAsync(any()))
                .thenReturn("test-request-id");

            mockMvc.perform(post("/api/v1/decide/async")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"rule-001\",\"script\":\"return true\",\"features\":{}}"))
                .andExpect(status().isAccepted());

            verify(asyncRuleExecutionService, times(1)).submitAsync(any());
        }

        @Test
        @DisplayName("带特征的请求应正常处理")
        void shouldHandleRequestWithFeatures() throws Exception {
            when(asyncRuleExecutionService.submitAsync(any()))
                .thenReturn("test-request-id-002");

            mockMvc.perform(post("/api/v1/decide/async")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"rule-002\",\"script\":\"return amount > 100\",\"features\":{\"amount\":5000},\"timeoutMs\":100}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").value("test-request-id-002"));
        }

        @Test
        @DisplayName("缺少 ruleId 应返回 400")
        void shouldRejectMissingRuleId() throws Exception {
            mockMvc.perform(post("/api/v1/decide/async")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"script\":\"return true\",\"features\":{}}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("空请求体应返回 400")
        void shouldRejectEmptyBody() throws Exception {
            mockMvc.perform(post("/api/v1/decide/async")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("缺少 Content-Type 应返回 415")
        void shouldRejectMissingContentType() throws Exception {
            mockMvc.perform(post("/api/v1/decide/async")
                    .content("{\"ruleId\":\"rule-001\",\"script\":\"return true\"}"))
                .andExpect(status().isUnsupportedMediaType());
        }
    }

    // ==================== 查询结果测试 ====================

    @Nested
    @DisplayName("查询异步结果测试")
    class QueryResultTests {

        @Test
        @DisplayName("GET /api/v1/decide/async/{requestId} 已完成时应返回 COMPLETED")
        void shouldReturnCompletedResult() throws Exception {
            DecisionResponse response = DecisionResponse.builder()
                .decision("PASS")
                .reason("规则执行完成")
                .executionTimeMs(15)
                .timeout(false)
                .build();

            when(asyncRuleExecutionService.getAsyncResult("completed-id"))
                .thenReturn(response);

            mockMvc.perform(get("/api/v1/decide/async/completed-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("completed-id"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.decision").value("PASS"))
                .andExpect(jsonPath("$.reason").value("规则执行完成"))
                .andExpect(jsonPath("$.executionTimeMs").value(15))
                .andExpect(jsonPath("$.timeout").value(false));
        }

        @Test
        @DisplayName("GET /api/v1/decide/async/{requestId} 未完成时应返回 PROCESSING")
        void shouldReturnProcessingWhenNotCompleted() throws Exception {
            when(asyncRuleExecutionService.getAsyncResult("processing-id"))
                .thenReturn(null);

            mockMvc.perform(get("/api/v1/decide/async/processing-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("processing-id"))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        }

        @Test
        @DisplayName("超时的结果应正确返回 timeout 标志")
        void shouldReturnTimeoutResult() throws Exception {
            DecisionResponse response = DecisionResponse.builder()
                .decision("PASS")
                .reason("规则执行超时，返回默认通过决策")
                .executionTimeMs(50)
                .timeout(true)
                .build();

            when(asyncRuleExecutionService.getAsyncResult("timeout-id"))
                .thenReturn(response);

            mockMvc.perform(get("/api/v1/decide/async/timeout-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.decision").value("PASS"))
                .andExpect(jsonPath("$.timeout").value(true));
        }

        @Test
        @DisplayName("REJECT 决策应正确返回")
        void shouldReturnRejectDecision() throws Exception {
            DecisionResponse response = DecisionResponse.builder()
                .decision("REJECT")
                .reason("金额超过阈值")
                .executionTimeMs(20)
                .timeout(false)
                .build();

            when(asyncRuleExecutionService.getAsyncResult("reject-id"))
                .thenReturn(response);

            mockMvc.perform(get("/api/v1/decide/async/reject-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REJECT"))
                .andExpect(jsonPath("$.reason").value("金额超过阈值"));
        }
    }

    // ==================== 响应格式验证 ====================

    @Nested
    @DisplayName("响应格式验证")
    class ResponseFormatTests {

        @Test
        @DisplayName("提交请求的响应应包含 requestId 和 status")
        void shouldContainRequestIdAndStatus() throws Exception {
            when(asyncRuleExecutionService.submitAsync(any()))
                .thenReturn("format-test-id");

            mockMvc.perform(post("/api/v1/decide/async")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"rule-001\",\"script\":\"return true\",\"features\":{}}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.status").exists());
        }

        @Test
        @DisplayName("响应 Content-Type 应为 application/json")
        void shouldReturnJsonContentType() throws Exception {
            when(asyncRuleExecutionService.submitAsync(any()))
                .thenReturn("json-test-id");

            mockMvc.perform(post("/api/v1/decide/async")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleId\":\"rule-001\",\"script\":\"return true\",\"features\":{}}"))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }
    }
}
