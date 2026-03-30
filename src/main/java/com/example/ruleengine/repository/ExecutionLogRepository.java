package com.example.ruleengine.repository;

import com.example.ruleengine.domain.ExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 规则执行日志数据访问接口
 */
@Repository
public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, Long> {

    /**
     * 根据规则Key查询执行日志（按创建时间倒序）
     */
    List<ExecutionLog> findByRuleKeyOrderByCreatedAtDesc(String ruleKey);

    /**
     * 根据规则Key和时间范围查询执行日志
     */
    List<ExecutionLog> findByRuleKeyAndCreatedAtBetweenOrderByCreatedAtDesc(
            String ruleKey, LocalDateTime start, LocalDateTime end);

    /**
     * 根据状态查询执行日志
     */
    List<ExecutionLog> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * 根据时间范围查询执行日志
     */
    List<ExecutionLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime start, LocalDateTime end);

    /**
     * 查询最近N条执行日志
     */
    List<ExecutionLog> findTop100ByOrderByCreatedAtDesc();

    /**
     * 根据规则Key和状态查询
     */
    List<ExecutionLog> findByRuleKeyAndStatusOrderByCreatedAtDesc(
            String ruleKey, String status);

    /**
     * 删除指定时间之前的执行日志（用于日志保留策略）
     */
    @Modifying
    @Query("DELETE FROM ExecutionLog e WHERE e.createdAt < :before")
    int deleteByCreatedAtBefore(@Param("before") LocalDateTime before);
}
