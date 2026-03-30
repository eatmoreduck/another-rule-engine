package com.example.ruleengine.metrics;

import com.example.ruleengine.model.dto.ExecutionStats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RuleExecutionMetrics 单元测试
 * MON-01: 验证规则执行统计指标的正确记录和查询
 */
@DisplayName("RuleExecutionMetrics 测试")
class RuleExecutionMetricsTest {

    private MeterRegistry meterRegistry;
    private RuleExecutionMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new RuleExecutionMetrics(meterRegistry);
    }

    @Nested
    @DisplayName("recordExecution 测试")
    class RecordExecutionTests {

        @Test
        @DisplayName("记录 PASS 执行应增加 total 和 hit 计数")
        void shouldIncrementTotalAndHitOnPass() {
            // Act
            metrics.recordExecution("rule-001", "PASS", 10);

            // Assert
            ExecutionStats stats = metrics.getExecutionStats("rule-001");
            assertEquals(1, stats.getTotalExecutions());
            assertEquals(1, stats.getHitCount());
            assertEquals(0, stats.getErrorCount());
        }

        @Test
        @DisplayName("记录 REJECT 执行应只增加 total 计数，不增加 hit")
        void shouldIncrementOnlyTotalOnReject() {
            // Act
            metrics.recordExecution("rule-002", "REJECT", 15);

            // Assert
            ExecutionStats stats = metrics.getExecutionStats("rule-002");
            assertEquals(1, stats.getTotalExecutions());
            assertEquals(0, stats.getHitCount());
        }

        @Test
        @DisplayName("多次执行应累加计数")
        void shouldAccumulateCounts() {
            // Act
            metrics.recordExecution("rule-003", "PASS", 5);
            metrics.recordExecution("rule-003", "REJECT", 8);
            metrics.recordExecution("rule-003", "PASS", 12);

            // Assert
            ExecutionStats stats = metrics.getExecutionStats("rule-003");
            assertEquals(3, stats.getTotalExecutions());
            assertEquals(2, stats.getHitCount());
        }

        @Test
        @DisplayName("不同规则应独立计数")
        void shouldTrackDifferentRulesIndependently() {
            // Act
            metrics.recordExecution("rule-A", "PASS", 10);
            metrics.recordExecution("rule-B", "REJECT", 20);
            metrics.recordExecution("rule-A", "PASS", 15);

            // Assert
            ExecutionStats statsA = metrics.getExecutionStats("rule-A");
            ExecutionStats statsB = metrics.getExecutionStats("rule-B");

            assertEquals(2, statsA.getTotalExecutions());
            assertEquals(2, statsA.getHitCount());

            assertEquals(1, statsB.getTotalExecutions());
            assertEquals(0, statsB.getHitCount());
        }
    }

    @Nested
    @DisplayName("recordError 测试")
    class RecordErrorTests {

        @Test
        @DisplayName("记录错误应增加 error 计数")
        void shouldIncrementErrorCount() {
            // Act
            metrics.recordError("rule-004", new RuntimeException("test error"));

            // Assert
            ExecutionStats stats = metrics.getExecutionStats("rule-004");
            assertEquals(1, stats.getErrorCount());
        }

        @Test
        @DisplayName("多次错误应累加计数")
        void shouldAccumulateErrors() {
            // Act
            metrics.recordError("rule-005", new RuntimeException("error 1"));
            metrics.recordError("rule-005", new IllegalArgumentException("error 2"));

            // Assert
            ExecutionStats stats = metrics.getExecutionStats("rule-005");
            assertEquals(2, stats.getErrorCount());
        }
    }

    @Nested
    @DisplayName("getExecutionStats 测试")
    class GetExecutionStatsTests {

        @Test
        @DisplayName("未记录过的规则应返回零值统计")
        void shouldReturnZeroStatsForUnknownRule() {
            // Act
            ExecutionStats stats = metrics.getExecutionStats("unknown-rule");

            // Assert
            assertNotNull(stats);
            assertEquals(0, stats.getTotalExecutions());
            assertEquals(0, stats.getHitCount());
            assertEquals(0, stats.getErrorCount());
            assertEquals(0.0, stats.getAvgExecutionTimeMs());
            assertNull(stats.getLastExecutedAt());
        }

        @Test
        @DisplayName("应返回正确的平均执行时间")
        void shouldReturnCorrectAvgExecutionTime() {
            // Act
            metrics.recordExecution("rule-006", "PASS", 10);
            metrics.recordExecution("rule-006", "PASS", 20);

            // Assert
            ExecutionStats stats = metrics.getExecutionStats("rule-006");
            assertEquals(2, stats.getTotalExecutions());
            assertTrue(stats.getAvgExecutionTimeMs() > 0);
        }

        @Test
        @DisplayName("应记录最后执行时间")
        void shouldRecordLastExecutedAt() {
            // Act
            metrics.recordExecution("rule-007", "PASS", 5);

            // Assert
            ExecutionStats stats = metrics.getExecutionStats("rule-007");
            assertNotNull(stats.getLastExecutedAt());
        }

        @Test
        @DisplayName("完整场景测试：混合执行和错误")
        void shouldHandleMixedExecutionAndErrors() {
            // Act
            metrics.recordExecution("rule-008", "PASS", 5);
            metrics.recordExecution("rule-008", "REJECT", 10);
            metrics.recordError("rule-008", new RuntimeException("error"));
            metrics.recordExecution("rule-008", "PASS", 8);

            // Assert
            ExecutionStats stats = metrics.getExecutionStats("rule-008");
            assertEquals(3, stats.getTotalExecutions());
            assertEquals(2, stats.getHitCount());
            assertEquals(1, stats.getErrorCount());
            assertTrue(stats.getAvgExecutionTimeMs() > 0);
            assertNotNull(stats.getLastExecutedAt());
        }
    }
}
