package com.example.ruleengine.repository;

import com.example.ruleengine.domain.GrayscaleMetric;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
