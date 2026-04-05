package com.example.ruleengine.repository;

import com.example.ruleengine.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志数据访问接口
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * 根据实体类型和实体ID查询审计日志
     */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByOperationTimeDesc(
            String entityType, String entityId);

    /**
     * 根据操作人查询审计日志
     */
    List<AuditLog> findByOperatorOrderByOperationTimeDesc(String operator);

    /**
     * 根据时间范围查询审计日志
     */
    List<AuditLog> findByOperationTimeBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 根据请求ID查询审计日志
     */
    List<AuditLog> findByRequestId(String requestId);

    /**
     * 根据实体类型、ID和操作类型查询
     */
    @Query("SELECT a FROM AuditLog a WHERE a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.operationTime DESC")
    List<AuditLog> findByEntityTypeAndEntityIdOrderByOperationTimeDescQuery(
            @Param("entityType") String entityType,
            @Param("entityId") String entityId);

    /**
     * 综合分页查询审计日志（支持多条件过滤）
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:operator IS NULL OR a.operator LIKE CONCAT('%', :operator, '%')) AND " +
           "(:entityType IS NULL OR a.entityType = :entityType) AND " +
           "(:operation IS NULL OR a.operation = :operation) AND " +
           "(:startTime IS NULL OR a.operationTime >= :startTime) AND " +
           "(:endTime IS NULL OR a.operationTime <= :endTime)")
    Page<AuditLog> findByConditions(
            @Param("operator") String operator,
            @Param("entityType") String entityType,
            @Param("operation") String operation,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable
    );
}
