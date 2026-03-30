package com.example.ruleengine.service.grayscale;

import com.example.ruleengine.constants.GrayscaleStatus;
import com.example.ruleengine.domain.GrayscaleConfig;
import com.example.ruleengine.domain.GrayscaleMetric;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.domain.RuleVersion;
import com.example.ruleengine.model.dto.CreateGrayscaleRequest;
import com.example.ruleengine.model.dto.GrayscaleConfigResponse;
import com.example.ruleengine.model.dto.GrayscaleReportResponse;
import com.example.ruleengine.model.dto.GrayscaleReportResponse.VersionMetrics;
import com.example.ruleengine.repository.GrayscaleConfigRepository;
import com.example.ruleengine.repository.GrayscaleMetricRepository;
import com.example.ruleengine.repository.RuleRepository;
import com.example.ruleengine.repository.RuleVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 灰度发布服务
 * VER-03: 灰度发布功能
 * VER-04: 灰度效果对比
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GrayscaleService {

    private final GrayscaleConfigRepository grayscaleConfigRepository;
    private final GrayscaleMetricRepository grayscaleMetricRepository;
    private final RuleRepository ruleRepository;
    private final RuleVersionRepository ruleVersionRepository;

    /**
     * 创建灰度配置
     */
    @Transactional
    public GrayscaleConfigResponse createGrayscaleConfig(
            CreateGrayscaleRequest request, String operator) {
        log.info("创建灰度配置: ruleKey={}, grayscaleVersion={}, percentage={}, operator={}",
                request.getRuleKey(), request.getGrayscaleVersion(),
                request.getGrayscalePercentage(), operator);

        // 1. 验证规则存在
        Rule rule = ruleRepository.findByRuleKey(request.getRuleKey())
                .orElseThrow(() -> new IllegalArgumentException(
                        "规则不存在: " + request.getRuleKey()));

        // 2. 验证没有运行中的灰度配置
        if (grayscaleConfigRepository.existsByRuleKeyAndStatus(
                request.getRuleKey(), GrayscaleStatus.RUNNING)) {
            throw new IllegalStateException(
                    "规则已有运行中的灰度配置: " + request.getRuleKey());
        }

        // 3. 验证灰度版本存在
        ruleVersionRepository.findByRuleKeyAndVersion(
                request.getRuleKey(), request.getGrayscaleVersion())
                .orElseThrow(() -> new IllegalArgumentException(
                        "灰度版本不存在: " + request.getRuleKey()
                                + " version=" + request.getGrayscaleVersion()));

        // 4. 创建灰度配置
        GrayscaleConfig config = GrayscaleConfig.builder()
                .ruleKey(request.getRuleKey())
                .currentVersion(rule.getVersion())
                .grayscaleVersion(request.getGrayscaleVersion())
                .grayscalePercentage(request.getGrayscalePercentage())
                .status(GrayscaleStatus.DRAFT)
                .createdBy(operator)
                .build();

        config = grayscaleConfigRepository.save(config);

        // 5. 初始化指标记录（当前版本和灰度版本各一条）
        initMetrics(config);

        log.info("灰度配置创建成功: id={}", config.getId());
        return GrayscaleConfigResponse.fromEntity(config);
    }

    /**
     * 启动灰度
     */
    @Transactional
    public GrayscaleConfigResponse startGrayscale(Long configId) {
        log.info("启动灰度: configId={}", configId);

        GrayscaleConfig config = grayscaleConfigRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "灰度配置不存在: " + configId));

        if (config.getStatus() != GrayscaleStatus.DRAFT) {
            throw new IllegalStateException(
                    "只有草稿状态的灰度配置才能启动，当前状态: "
                            + config.getStatus().getDescription());
        }

        config.setStatus(GrayscaleStatus.RUNNING);
        config.setStartedAt(LocalDateTime.now());

        config = grayscaleConfigRepository.save(config);
        log.info("灰度已启动: configId={}, ruleKey={}", configId, config.getRuleKey());
        return GrayscaleConfigResponse.fromEntity(config);
    }

    /**
     * 暂停灰度（状态回到草稿）
     */
    @Transactional
    public GrayscaleConfigResponse pauseGrayscale(Long configId) {
        log.info("暂停灰度: configId={}", configId);

        GrayscaleConfig config = grayscaleConfigRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "灰度配置不存在: " + configId));

        if (config.getStatus() != GrayscaleStatus.RUNNING) {
            throw new IllegalStateException(
                    "只有运行中的灰度配置才能暂停，当前状态: "
                            + config.getStatus().getDescription());
        }

        config.setStatus(GrayscaleStatus.DRAFT);

        config = grayscaleConfigRepository.save(config);
        log.info("灰度已暂停: configId={}", configId);
        return GrayscaleConfigResponse.fromEntity(config);
    }

    /**
     * 完成灰度（全量切换到灰度版本）
     */
    @Transactional
    public GrayscaleConfigResponse completeGrayscale(Long configId) {
        log.info("完成灰度（全量切换）: configId={}", configId);

        GrayscaleConfig config = grayscaleConfigRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "灰度配置不存在: " + configId));

        if (config.getStatus() != GrayscaleStatus.RUNNING) {
            throw new IllegalStateException(
                    "只有运行中的灰度配置才能完成，当前状态: "
                            + config.getStatus().getDescription());
        }

        // 全量切换：将规则的当前版本更新为灰度版本
        final GrayscaleConfig finalConfig = config;
        String ruleKey = finalConfig.getRuleKey();
        int grayscaleVer = finalConfig.getGrayscaleVersion();

        Rule rule = ruleRepository.findByRuleKey(ruleKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "规则不存在: " + ruleKey));

        // 从版本历史获取灰度版本的脚本
        RuleVersion grayscaleVersion = ruleVersionRepository
                .findByRuleKeyAndVersion(ruleKey, grayscaleVer)
                .orElseThrow(() -> new IllegalArgumentException(
                        "灰度版本不存在: version=" + grayscaleVer));

        rule.setVersion(grayscaleVer);
        rule.setGroovyScript(grayscaleVersion.getGroovyScript());
        ruleRepository.save(rule);

        finalConfig.setStatus(GrayscaleStatus.COMPLETED);
        finalConfig.setCompletedAt(LocalDateTime.now());
        finalConfig.setGrayscalePercentage(100);

        GrayscaleConfig saved = grayscaleConfigRepository.save(finalConfig);
        log.info("灰度已完成，全量切换到版本 {}: configId={}",
                saved.getGrayscaleVersion(), configId);
        return GrayscaleConfigResponse.fromEntity(saved);
    }

    /**
     * 回滚灰度
     */
    @Transactional
    public GrayscaleConfigResponse rollbackGrayscale(Long configId) {
        log.info("回滚灰度: configId={}", configId);

        GrayscaleConfig config = grayscaleConfigRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "灰度配置不存在: " + configId));

        if (config.getStatus() == GrayscaleStatus.COMPLETED
                || config.getStatus() == GrayscaleStatus.ROLLED_BACK) {
            throw new IllegalStateException(
                    "已完成或已回滚的灰度配置不能再次回滚，当前状态: "
                            + config.getStatus().getDescription());
        }

        // 恢复到当前版本（不修改规则内容，因为规则尚未被切换）
        config.setStatus(GrayscaleStatus.ROLLED_BACK);
        config.setCompletedAt(LocalDateTime.now());
        config.setGrayscalePercentage(0);

        config = grayscaleConfigRepository.save(config);
        log.info("灰度已回滚: configId={}", configId);
        return GrayscaleConfigResponse.fromEntity(config);
    }

    /**
     * 判断指定规则是否应使用灰度版本
     * 核心分流逻辑：按百分比随机分流
     *
     * @param ruleKey 规则Key
     * @return 如果应使用灰度版本，返回灰度版本号；否则返回空
     */
    public Optional<Integer> resolveGrayscaleVersion(String ruleKey) {
        Optional<GrayscaleConfig> runningConfig =
                grayscaleConfigRepository.findByRuleKeyAndStatus(
                        ruleKey, GrayscaleStatus.RUNNING);

        if (runningConfig.isEmpty()) {
            return Optional.empty();
        }

        GrayscaleConfig config = runningConfig.get();
        int percentage = config.getGrayscalePercentage();

        // 按百分比随机分流
        int randomValue = ThreadLocalRandom.current().nextInt(100);
        if (randomValue < percentage) {
            log.debug("灰度分流命中: ruleKey={}, grayscaleVersion={}, random={}, percentage={}",
                    ruleKey, config.getGrayscaleVersion(), randomValue, percentage);
            return Optional.of(config.getGrayscaleVersion());
        }

        return Optional.empty();
    }

    /**
     * 获取灰度配置（运行中）
     */
    public Optional<GrayscaleConfig> getRunningConfig(String ruleKey) {
        return grayscaleConfigRepository.findByRuleKeyAndStatus(
                ruleKey, GrayscaleStatus.RUNNING);
    }

    /**
     * 记录灰度执行指标
     *
     * @param configId  灰度配置ID
     * @param version   执行的版本号
     * @param executionTimeMs 执行耗时
     * @param isSuccess 是否成功
     */
    @Transactional
    public void recordMetrics(Long configId, Integer version,
                              long executionTimeMs, boolean isSuccess) {
        Optional<GrayscaleMetric> metricOpt =
                grayscaleMetricRepository.findByGrayscaleConfigIdAndVersion(
                        configId, version);

        if (metricOpt.isEmpty()) {
            log.warn("灰度指标记录不存在: configId={}, version={}", configId, version);
            return;
        }

        GrayscaleMetric metric = metricOpt.get();
        metric.setExecutionCount(metric.getExecutionCount() + 1);

        // 计算新的平均执行时间
        int totalExecTime = metric.getAvgExecutionTimeMs()
                * (metric.getExecutionCount() - 1)
                + (int) executionTimeMs;
        metric.setAvgExecutionTimeMs(
                totalExecTime / metric.getExecutionCount());

        if (isSuccess) {
            metric.setHitCount(metric.getHitCount() + 1);
        } else {
            metric.setErrorCount(metric.getErrorCount() + 1);
        }

        grayscaleMetricRepository.save(metric);
    }

    /**
     * 获取灰度对比报告
     */
    public GrayscaleReportResponse getGrayscaleReport(Long configId) {
        log.info("获取灰度对比报告: configId={}", configId);

        GrayscaleConfig config = grayscaleConfigRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "灰度配置不存在: " + configId));

        List<GrayscaleMetric> metrics =
                grayscaleMetricRepository.findByGrayscaleConfigId(configId);

        VersionMetrics currentMetrics = buildVersionMetrics(
                config.getCurrentVersion(), metrics);
        VersionMetrics grayscaleMetrics = buildVersionMetrics(
                config.getGrayscaleVersion(), metrics);

        return GrayscaleReportResponse.builder()
                .configId(config.getId())
                .ruleKey(config.getRuleKey())
                .currentVersion(config.getCurrentVersion())
                .grayscaleVersion(config.getGrayscaleVersion())
                .grayscalePercentage(config.getGrayscalePercentage())
                .currentVersionMetrics(currentMetrics)
                .grayscaleVersionMetrics(grayscaleMetrics)
                .build();
    }

    /**
     * 获取规则的所有灰度配置
     */
    public List<GrayscaleConfigResponse> getGrayscaleConfigs(String ruleKey) {
        List<GrayscaleConfig> configs =
                grayscaleConfigRepository.findByRuleKeyOrderByCreatedAtDesc(ruleKey);
        return configs.stream()
                .map(GrayscaleConfigResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // ========== 私有方法 ==========

    /**
     * 初始化灰度指标
     */
    private void initMetrics(GrayscaleConfig config) {
        // 当前版本指标
        GrayscaleMetric currentMetric = GrayscaleMetric.builder()
                .grayscaleConfigId(config.getId())
                .version(config.getCurrentVersion())
                .executionCount(0)
                .hitCount(0)
                .errorCount(0)
                .avgExecutionTimeMs(0)
                .build();
        grayscaleMetricRepository.save(currentMetric);

        // 灰度版本指标
        GrayscaleMetric grayscaleMetric = GrayscaleMetric.builder()
                .grayscaleConfigId(config.getId())
                .version(config.getGrayscaleVersion())
                .executionCount(0)
                .hitCount(0)
                .errorCount(0)
                .avgExecutionTimeMs(0)
                .build();
        grayscaleMetricRepository.save(grayscaleMetric);
    }

    /**
     * 构建版本指标
     */
    private VersionMetrics buildVersionMetrics(
            Integer version, List<GrayscaleMetric> metrics) {
        return metrics.stream()
                .filter(m -> m.getVersion().equals(version))
                .findFirst()
                .map(m -> {
                    double errorRate = m.getExecutionCount() > 0
                            ? (double) m.getErrorCount() / m.getExecutionCount() * 100
                            : 0.0;
                    double hitRate = m.getExecutionCount() > 0
                            ? (double) m.getHitCount() / m.getExecutionCount() * 100
                            : 0.0;
                    return VersionMetrics.builder()
                            .version(m.getVersion())
                            .executionCount(m.getExecutionCount())
                            .hitCount(m.getHitCount())
                            .errorCount(m.getErrorCount())
                            .avgExecutionTimeMs(m.getAvgExecutionTimeMs())
                            .errorRate(Math.round(errorRate * 100.0) / 100.0)
                            .hitRate(Math.round(hitRate * 100.0) / 100.0)
                            .build();
                })
                .orElse(VersionMetrics.builder()
                        .version(version)
                        .executionCount(0)
                        .hitCount(0)
                        .errorCount(0)
                        .avgExecutionTimeMs(0)
                        .errorRate(0.0)
                        .hitRate(0.0)
                        .build());
    }
}
