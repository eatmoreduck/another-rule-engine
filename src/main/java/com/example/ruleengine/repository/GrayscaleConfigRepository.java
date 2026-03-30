package com.example.ruleengine.repository;

import com.example.ruleengine.constants.GrayscaleStatus;
import com.example.ruleengine.domain.GrayscaleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 灰度发布配置数据访问接口
 */
@Repository
public interface GrayscaleConfigRepository extends JpaRepository<GrayscaleConfig, Long> {

    /**
     * 根据规则Key查询灰度配置（运行中）
     */
    Optional<GrayscaleConfig> findByRuleKeyAndStatus(String ruleKey, GrayscaleStatus status);

    /**
     * 根据规则Key查询所有灰度配置（按创建时间降序）
     */
    List<GrayscaleConfig> findByRuleKeyOrderByCreatedAtDesc(String ruleKey);

    /**
     * 根据状态查询灰度配置
     */
    List<GrayscaleConfig> findByStatus(GrayscaleStatus status);

    /**
     * 查询指定规则是否有运行中的灰度配置
     */
    boolean existsByRuleKeyAndStatus(String ruleKey, GrayscaleStatus status);
}
