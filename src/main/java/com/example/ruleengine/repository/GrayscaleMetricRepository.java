package com.example.ruleengine.repository;

import com.example.ruleengine.domain.GrayscaleMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 灰度指标数据访问接口
 */
@Repository
public interface GrayscaleMetricRepository extends JpaRepository<GrayscaleMetric, Long> {

    /**
     * 根据灰度配置ID查询所有指标
     */
    List<GrayscaleMetric> findByGrayscaleConfigId(Long grayscaleConfigId);

    /**
     * 根据灰度配置ID和版本号查询指标
     */
    Optional<GrayscaleMetric> findByGrayscaleConfigIdAndVersion(Long grayscaleConfigId, Integer version);

    /**
     * 原子递增指标计数器（防止并发更新丢失）
     * 使用数据库级别的原子操作替代 read-modify-write 模式
     *
     * @param configId 灰度配置ID
     * @param version  版本号
     * @param execTimeMs 执行耗时（毫秒）
     * @param isSuccess 是否成功
     * @return 更新的行数（0 表示记录不存在）
     */
    @Modifying
    @Query("UPDATE GrayscaleMetric m SET m.executionCount = m.executionCount + 1, " +
           "m.avgExecutionTimeMs = (m.avgExecutionTimeMs * m.executionCount + :execTime) / (m.executionCount + 1), " +
           "m.hitCount = m.hitCount + CASE WHEN :isSuccess = true THEN 1 ELSE 0 END, " +
           "m.errorCount = m.errorCount + CASE WHEN :isSuccess = false THEN 1 ELSE 0 END " +
           "WHERE m.grayscaleConfigId = :configId AND m.version = :version")
    int incrementMetrics(@Param("configId") Long configId, @Param("version") Integer version,
                         @Param("execTime") int execTime, @Param("isSuccess") boolean isSuccess);
}
