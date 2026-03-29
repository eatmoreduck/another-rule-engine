package com.example.ruleengine.service.audit;

import com.example.ruleengine.constants.AuditEvent;
import com.example.ruleengine.domain.AuditLog;
import com.example.ruleengine.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuditLogService 集成测试
 * 使用 H2 内存数据库替代 Testcontainers PostgreSQL
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AuditLogService 集成测试")
class AuditLogServiceTest {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("应该成功记录操作日志")
    void shouldLogOperation() throws InterruptedException {
        auditLogService.logOperation(
                "RULE",
                "test-rule-1",
                AuditEvent.RULE_CREATE,
                "test-user",
                Map.of("ruleName", "测试规则", "action", "create")
        );

        // 等待异步方法完成
        Thread.sleep(1000);

        List<AuditLog> logs = auditLogRepository.findByEntityTypeAndEntityIdOrderByOperationTimeDesc(
                "RULE", "test-rule-1");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getOperation()).isEqualTo("RULE_CREATE");
        assertThat(logs.get(0).getOperator()).isEqualTo("test-user");
    }

    @Test
    @DisplayName("应该成功获取实体审计历史")
    void shouldGetAuditHistory() throws InterruptedException {
        auditLogService.logOperation("RULE", "test-rule", AuditEvent.RULE_CREATE, "user1", Map.of());
        Thread.sleep(500);
        auditLogService.logOperation("RULE", "test-rule", AuditEvent.RULE_UPDATE, "user2", Map.of());
        Thread.sleep(500);

        List<AuditLog> history = auditLogService.getAuditHistory("RULE", "test-rule");

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getOperation()).isEqualTo("RULE_UPDATE");
        assertThat(history.get(1).getOperation()).isEqualTo("RULE_CREATE");
    }

    @Test
    @DisplayName("应该成功记录失败的操作")
    void shouldLogFailure() throws InterruptedException {
        auditLogService.logFailure(
                "RULE",
                "test-rule",
                AuditEvent.RULE_UPDATE,
                "test-user",
                "验证失败"
        );

        Thread.sleep(1000);

        List<AuditLog> logs = auditLogRepository.findByEntityTypeAndEntityIdOrderByOperationTimeDesc(
                "RULE", "test-rule");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(logs.get(0).getErrorMessage()).isEqualTo("验证失败");
    }
}
