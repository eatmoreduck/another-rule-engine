package com.example.ruleengine.controller;

import com.example.ruleengine.metrics.RuleExecutionMetrics;
import com.example.ruleengine.model.dto.ExecutionStats;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MetricsController 单元测试
 * MON-01: 验证监控指标 API 端点的正确性
 */
@DisplayName("MetricsController 测试")
@WebMvcTest(MetricsController.class)
@TestPropertySource(properties = "sa-token.auth-enabled=false")
class MetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RuleExecutionMetrics ruleExecutionMetrics;

    @MockBean
    private MeterRegistry meterRegistry;

    @Nested
    @DisplayName("GET /api/v1/metrics/rules/{ruleKey} 测试")
    class GetRuleMetricsTests {

        @Test
        @DisplayName("应返回指定规则的执行统计")
        void shouldReturnExecutionStats() throws Exception {
            // Arrange
            ExecutionStats stats = new ExecutionStats(
                100L, 60L, 5L, 12.5, 25.0, Instant.now()
            );
            when(ruleExecutionMetrics.getExecutionStats("rule-001")).thenReturn(stats);

            // Act & Assert
            mockMvc.perform(get("/api/v1/metrics/rules/rule-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExecutions").value(100))
                .andExpect(jsonPath("$.hitCount").value(60))
                .andExpect(jsonPath("$.errorCount").value(5))
                .andExpect(jsonPath("$.avgExecutionTimeMs").value(12.5))
                .andExpect(jsonPath("$.p95ExecutionTimeMs").value(25.0))
                .andExpect(jsonPath("$.lastExecutedAt").exists());
        }

        @Test
        @DisplayName("未记录过的规则应返回零值统计")
        void shouldReturnZeroStatsForUnknownRule() throws Exception {
            // Arrange
            ExecutionStats stats = new ExecutionStats(0, 0, 0, 0.0, 0.0, null);
            when(ruleExecutionMetrics.getExecutionStats("unknown-rule")).thenReturn(stats);

            // Act & Assert
            mockMvc.perform(get("/api/v1/metrics/rules/unknown-rule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExecutions").value(0))
                .andExpect(jsonPath("$.hitCount").value(0))
                .andExpect(jsonPath("$.errorCount").value(0));
        }

        @Test
        @DisplayName("响应 Content-Type 应为 application/json")
        void shouldReturnJsonContentType() throws Exception {
            // Arrange
            when(ruleExecutionMetrics.getExecutionStats("rule-001"))
                .thenReturn(new ExecutionStats());

            // Act & Assert
            mockMvc.perform(get("/api/v1/metrics/rules/rule-001"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("应正确传递 ruleKey 参数")
        void shouldPassCorrectRuleKey() throws Exception {
            // Arrange
            when(ruleExecutionMetrics.getExecutionStats("my-rule")).thenReturn(new ExecutionStats());

            // Act
            mockMvc.perform(get("/api/v1/metrics/rules/my-rule"))
                .andExpect(status().isOk());

            // Assert
            verify(ruleExecutionMetrics).getExecutionStats("my-rule");
        }
    }
}
