package com.example.ruleengine.service.audit;

import com.example.ruleengine.constants.AuditEvent;
import com.example.ruleengine.domain.AuditLog;
import com.example.ruleengine.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审计日志服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * 记录操作日志
     */
    @Async
    public void logOperation(String entityType, String entityId,
                             AuditEvent operation, String operator,
                             Map<String, Object> details) {
        try {
            String detailJson = objectMapper.writeValueAsString(details);
            AuditLog auditLog = AuditLog.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .operation(operation.name())
                    .operationDetail(detailJson)
                    .operator(operator)
                    .status("SUCCESS")
                    .build();
            auditLogRepository.save(auditLog);
        } catch (JsonProcessingException e) {
            log.error("序列化审计详情失败", e);
        }
    }

    /**
     * 记录操作日志（带上下文信息）
     */
    @Async
    public void logOperationWithContext(String entityType, String entityId,
                                       AuditEvent operation, String operator,
                                       Map<String, Object> details,
                                       String operatorIp, String requestId) {
        try {
            String detailJson = objectMapper.writeValueAsString(details);
            AuditLog auditLog = AuditLog.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .operation(operation.name())
                    .operationDetail(detailJson)
                    .operator(operator)
                    .operatorIp(operatorIp)
                    .requestId(requestId)
                    .status("SUCCESS")
                    .build();
            auditLogRepository.save(auditLog);
        } catch (JsonProcessingException e) {
            log.error("序列化审计详情失败", e);
        }
    }

    /**
     * 记录失败的操作
     */
    @Async
    public void logFailure(String entityType, String entityId,
                          AuditEvent operation, String operator,
                          String errorMessage) {
        AuditLog auditLog = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .operation(operation.name())
                .operator(operator)
                .status("FAILED")
                .errorMessage(errorMessage)
                .build();
        auditLogRepository.save(auditLog);
    }

    /**
     * 获取实体审计历史
     */
    public List<AuditLog> getAuditHistory(String entityType, String entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByOperationTimeDesc(
                entityType, entityId);
    }

    /**
     * 获取操作人活动记录
     */
    public List<AuditLog> getOperatorActivity(String operator,
                                             LocalDateTime start, LocalDateTime end) {
        return auditLogRepository.findByOperationTimeBetween(start, end).stream()
                .filter(log -> log.getOperator().equals(operator))
                .toList();
    }
}
