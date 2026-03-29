package com.example.ruleengine.service.audit;

import com.example.ruleengine.constants.AuditEvent;
import com.example.ruleengine.domain.AuditLog;
import com.example.ruleengine.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuditLogService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService 单元测试")
class AuditLogServiceUnitTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    @DisplayName("应该成功记录操作日志")
    void shouldLogOperation() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ruleName\":\"测试规则\"}");
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(AuditLog.builder().id(1L).build());

        // When
        auditLogService.logOperation(
                "RULE",
                "test-rule-1",
                AuditEvent.RULE_CREATE,
                "test-user",
                java.util.Map.of("ruleName", "测试规则", "action", "create")
        );

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());

        AuditLog savedLog = captor.getValue();
        assertThat(savedLog.getEntityType()).isEqualTo("RULE");
        assertThat(savedLog.getEntityId()).isEqualTo("test-rule-1");
        assertThat(savedLog.getOperation()).isEqualTo("RULE_CREATE");
        assertThat(savedLog.getOperator()).isEqualTo("test-user");
        assertThat(savedLog.getStatus()).isEqualTo("SUCCESS");
        assertThat(savedLog.getOperationDetail()).contains("ruleName");
    }

    @Test
    @DisplayName("应该成功记录操作日志（带上下文）")
    void shouldLogOperationWithContext() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"field\":\"condition\"}");
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(AuditLog.builder().id(1L).build());

        // When
        auditLogService.logOperationWithContext(
                "RULE",
                "test-rule-2",
                AuditEvent.RULE_UPDATE,
                "test-user",
                java.util.Map.of("field", "condition", "oldValue", "A", "newValue", "B"),
                "192.168.1.100",
                "req-123"
        );

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());

        AuditLog savedLog = captor.getValue();
        assertThat(savedLog.getEntityType()).isEqualTo("RULE");
        assertThat(savedLog.getEntityId()).isEqualTo("test-rule-2");
        assertThat(savedLog.getOperation()).isEqualTo("RULE_UPDATE");
        assertThat(savedLog.getOperatorIp()).isEqualTo("192.168.1.100");
        assertThat(savedLog.getRequestId()).isEqualTo("req-123");
        assertThat(savedLog.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("应该成功记录失败的操作")
    void shouldLogFailure() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(AuditLog.builder().id(1L).build());

        // When
        auditLogService.logFailure(
                "RULE",
                "test-rule",
                AuditEvent.RULE_UPDATE,
                "test-user",
                "验证失败"
        );

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());

        AuditLog savedLog = captor.getValue();
        assertThat(savedLog.getEntityType()).isEqualTo("RULE");
        assertThat(savedLog.getEntityId()).isEqualTo("test-rule");
        assertThat(savedLog.getOperation()).isEqualTo("RULE_UPDATE");
        assertThat(savedLog.getOperator()).isEqualTo("test-user");
        assertThat(savedLog.getStatus()).isEqualTo("FAILED");
        assertThat(savedLog.getErrorMessage()).isEqualTo("验证失败");
    }

    @Test
    @DisplayName("应该成功获取实体审计历史")
    void shouldGetAuditHistory() {
        // Given
        AuditLog log1 = AuditLog.builder()
                .id(1L)
                .entityType("RULE")
                .entityId("test-rule")
                .operation("RULE_CREATE")
                .operationTime(LocalDateTime.now().minusDays(1))
                .build();
        AuditLog log2 = AuditLog.builder()
                .id(2L)
                .entityType("RULE")
                .entityId("test-rule")
                .operation("RULE_UPDATE")
                .operationTime(LocalDateTime.now())
                .build();

        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByOperationTimeDesc("RULE", "test-rule"))
                .thenReturn(List.of(log2, log1));

        // When
        List<AuditLog> history = auditLogService.getAuditHistory("RULE", "test-rule");

        // Then
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getOperation()).isEqualTo("RULE_UPDATE");
        assertThat(history.get(1).getOperation()).isEqualTo("RULE_CREATE");

        verify(auditLogRepository, times(1))
                .findByEntityTypeAndEntityIdOrderByOperationTimeDesc("RULE", "test-rule");
    }

    @Test
    @DisplayName("应该成功获取操作人活动记录")
    void shouldGetOperatorActivity() {
        // Given
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();

        AuditLog log1 = AuditLog.builder()
                .id(1L)
                .operator("user1")
                .operation("RULE_CREATE")
                .operationTime(LocalDateTime.now().minusDays(1))
                .build();
        AuditLog log2 = AuditLog.builder()
                .id(2L)
                .operator("user2")
                .operation("RULE_UPDATE")
                .operationTime(LocalDateTime.now())
                .build();
        AuditLog log3 = AuditLog.builder()
                .id(3L)
                .operator("user1")
                .operation("RULE_DELETE")
                .operationTime(LocalDateTime.now().minusHours(1))
                .build();

        when(auditLogRepository.findByOperationTimeBetween(start, end))
                .thenReturn(List.of(log3, log2, log1));

        // When
        List<AuditLog> activity = auditLogService.getOperatorActivity("user1", start, end);

        // Then
        assertThat(activity).hasSize(2);
        assertThat(activity).allMatch(log -> log.getOperator().equals("user1"));

        verify(auditLogRepository, times(1)).findByOperationTimeBetween(start, end);
    }
}
