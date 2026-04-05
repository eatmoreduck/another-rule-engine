package com.example.ruleengine.service.grayscale;

import com.example.ruleengine.constants.GrayscaleStatus;
import com.example.ruleengine.domain.DecisionFlow;
import com.example.ruleengine.domain.DecisionFlowVersion;
import com.example.ruleengine.domain.GrayscaleConfig;
import com.example.ruleengine.domain.GrayscaleMetric;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.domain.RuleVersion;
import com.example.ruleengine.model.dto.CreateGrayscaleRequest;
import com.example.ruleengine.model.dto.GrayscaleConfigResponse;
import com.example.ruleengine.model.dto.GrayscaleReportResponse;
import com.example.ruleengine.model.dto.GrayscaleReportResponse.VersionMetrics;
import com.example.ruleengine.repository.DecisionFlowRepository;
import com.example.ruleengine.repository.DecisionFlowVersionRepository;
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
import java.util.Map;
import java.util.Optional;
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
    private final DecisionFlowRepository decisionFlowRepository;
    private final DecisionFlowVersionRepository decisionFlowVersionRepository;
    private final CanaryStrategyMatcher canaryStrategyMatcher;

    /**
     * 创建灰度配置（支持规则和决策流）
     */
    @Transactional
    public GrayscaleConfigResponse createGrayscaleConfig(
            CreateGrayscaleRequest request, String operator) {
        // 解析目标类型和目标 Key
        String targetType = request.getTargetType() != null ? request.getTargetType() : "RULE";
        String targetKey = resolveTargetKey(request);

        if (targetKey == null || targetKey.isBlank()) {
            throw new IllegalArgumentException("目标 Key 不能为空（ruleKey 或 targetKey）");
        }

        log.info("创建灰度配置: targetType={}, targetKey={}, grayscaleVersion={}, percentage={}, strategy={}, operator={}",
                targetType, targetKey, request.getGrayscaleVersion(),
                request.getGrayscalePercentage(), request.getStrategyType(), operator);

        int currentVersion;

        if ("DECISION_FLOW".equals(targetType)) {
            // 决策流灰度
            DecisionFlow flow = decisionFlowRepository.findByFlowKey(targetKey)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "决策流不存在: " + targetKey));

            // 验证没有运行中的灰度配置
            if (grayscaleConfigRepository.findByTargetTypeAndTargetKeyAndStatus(
                    "DECISION_FLOW", targetKey, GrayscaleStatus.RUNNING).isPresent()) {
                throw new IllegalStateException(
                        "决策流已有运行中的灰度配置: " + targetKey);
            }

            // 验证灰度版本存在
            decisionFlowVersionRepository.findByFlowKeyAndVersion(
                    targetKey, request.getGrayscaleVersion())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "决策流灰度版本不存在: " + targetKey
                                    + " version=" + request.getGrayscaleVersion()));

            currentVersion = flow.getVersion();
        } else {
            // 规则灰度（向后兼容）
            Rule rule = ruleRepository.findByRuleKey(targetKey)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "规则不存在: " + targetKey));

            // 验证没有运行中的灰度配置
            if (grayscaleConfigRepository.existsByRuleKeyAndStatus(
                    targetKey, GrayscaleStatus.RUNNING)) {
                throw new IllegalStateException(
                        "规则已有运行中的灰度配置: " + targetKey);
            }

            // 验证灰度版本存在
            ruleVersionRepository.findByRuleKeyAndVersion(
                    targetKey, request.getGrayscaleVersion())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "灰度版本不存在: " + targetKey
                                    + " version=" + request.getGrayscaleVersion()));

            currentVersion = rule.getVersion();
        }

        // 创建灰度配置
        GrayscaleConfig config = GrayscaleConfig.builder()
                .ruleKey(targetKey)
                .targetType(targetType)
                .targetKey(targetKey)
                .currentVersion(currentVersion)
                .grayscaleVersion(request.getGrayscaleVersion())
                .grayscalePercentage(request.getGrayscalePercentage())
                .status(GrayscaleStatus.DRAFT)
                .strategyType(request.getStrategyType() != null ? request.getStrategyType() : "PERCENTAGE")
                .featureRules(request.getFeatureRules())
                .whitelistIds(request.getWhitelistIds())
                .dualRunEnabled(request.getDualRunEnabled() != null ? request.getDualRunEnabled() : false)
                .createdBy(operator)
                .build();

        config = grayscaleConfigRepository.save(config);

        // 初始化指标记录
        initMetrics(config);

        log.info("灰度配置创建成功: id={}, targetType={}, targetKey={}",
                config.getId(), targetType, targetKey);
        return GrayscaleConfigResponse.fromEntity(config);
    }

    /**
     * 解析目标 Key
     * 优先使用 targetKey，其次使用 ruleKey
     */
    private String resolveTargetKey(CreateGrayscaleRequest request) {
        if (request.getTargetKey() != null && !request.getTargetKey().isBlank()) {
            return request.getTargetKey();
        }
        return request.getRuleKey();
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
     * 支持规则和决策流
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

        String targetKey = config.getTargetKey() != null ? config.getTargetKey() : config.getRuleKey();
        int grayscaleVer = config.getGrayscaleVersion();
        String targetType = config.getTargetType() != null ? config.getTargetType() : "RULE";

        if ("DECISION_FLOW".equals(targetType)) {
            // 决策流全量切换
            completeDecisionFlowGrayscale(targetKey, grayscaleVer);
        } else {
            // 规则全量切换
            completeRuleGrayscale(targetKey, grayscaleVer);
        }

        config.setStatus(GrayscaleStatus.COMPLETED);
        config.setCompletedAt(LocalDateTime.now());
        config.setGrayscalePercentage(100);

        GrayscaleConfig saved = grayscaleConfigRepository.save(config);
        log.info("灰度已完成，全量切换到版本 {}: configId={}, targetType={}",
                saved.getGrayscaleVersion(), configId, targetType);
        return GrayscaleConfigResponse.fromEntity(saved);
    }

    /**
     * 规则灰度全量切换
     */
    private void completeRuleGrayscale(String ruleKey, int grayscaleVer) {
        Rule rule = ruleRepository.findByRuleKey(ruleKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "规则不存在: " + ruleKey));

        RuleVersion grayscaleVersion = ruleVersionRepository
                .findByRuleKeyAndVersion(ruleKey, grayscaleVer)
                .orElseThrow(() -> new IllegalArgumentException(
                        "灰度版本不存在: version=" + grayscaleVer));

        rule.setVersion(grayscaleVer);
        rule.setGroovyScript(grayscaleVersion.getGroovyScript());
        ruleRepository.save(rule);
    }

    /**
     * 决策流灰度全量切换
     */
    private void completeDecisionFlowGrayscale(String flowKey, int grayscaleVer) {
        DecisionFlow flow = decisionFlowRepository.findByFlowKey(flowKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "决策流不存在: " + flowKey));

        DecisionFlowVersion grayscaleVersion = decisionFlowVersionRepository
                .findByFlowKeyAndVersion(flowKey, grayscaleVer)
                .orElseThrow(() -> new IllegalArgumentException(
                        "决策流灰度版本不存在: version=" + grayscaleVer));

        flow.setVersion(grayscaleVer);
        flow.setFlowGraph(grayscaleVersion.getFlowGraph());
        decisionFlowRepository.save(flow);
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
     * 判断指定规则是否应使用灰度版本（兼容旧调用，无 features 时退化为百分比分流）
     *
     * @param ruleKey 规则Key
     * @return 如果应使用灰度版本，返回灰度版本号；否则返回空
     */
    public Optional<Integer> resolveGrayscaleVersion(String ruleKey) {
        return resolveGrayscaleVersion(ruleKey, Map.of());
    }

    /**
     * 判断指定规则是否应使用灰度版本（支持多策略匹配）
     *
     * @param ruleKey  规则Key
     * @param features 请求特征数据
     * @return 如果应使用灰度版本，返回灰度版本号；否则返回空
     */
    public Optional<Integer> resolveGrayscaleVersion(String ruleKey, Map<String, Object> features) {
        try {
            Optional<GrayscaleConfig> runningConfig =
                    grayscaleConfigRepository.findByRuleKeyAndStatus(
                            ruleKey, GrayscaleStatus.RUNNING);

            if (runningConfig.isEmpty()) {
                return Optional.empty();
            }

            GrayscaleConfig config = runningConfig.get();
            boolean matched = canaryStrategyMatcher.matches(config, features);

            if (matched) {
                log.debug("灰度分流命中: ruleKey={}, strategy={}, grayscaleVersion={}",
                        ruleKey, config.getStrategyType(), config.getGrayscaleVersion());
                return Optional.of(config.getGrayscaleVersion());
            }

            return Optional.empty();
        } catch (Exception e) {
            log.error("灰度分流异常, fallback 到当前版本: ruleKey={}", ruleKey, e);
            return Optional.empty();
        }
    }

    /**
     * 判断指定决策流是否应使用灰度版本
     *
     * @param flowKey  决策流Key
     * @param features 请求特征数据
     * @return 如果应使用灰度版本，返回灰度版本号；否则返回空
     */
    public Optional<Integer> resolveGrayscaleVersionForFlow(String flowKey, Map<String, Object> features) {
        try {
            Optional<GrayscaleConfig> runningConfig =
                    grayscaleConfigRepository.findByTargetTypeAndTargetKeyAndStatus(
                            "DECISION_FLOW", flowKey, GrayscaleStatus.RUNNING);

            if (runningConfig.isEmpty()) {
                return Optional.empty();
            }

            GrayscaleConfig config = runningConfig.get();
            boolean matched = canaryStrategyMatcher.matches(config, features);

            if (matched) {
                log.debug("决策流灰度分流命中: flowKey={}, strategy={}, grayscaleVersion={}",
                        flowKey, config.getStrategyType(), config.getGrayscaleVersion());
                return Optional.of(config.getGrayscaleVersion());
            }

            return Optional.empty();
        } catch (Exception e) {
            log.error("决策流灰度分流异常, fallback 到当前版本: flowKey={}", flowKey, e);
            return Optional.empty();
        }
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

    /**
     * 查询灰度配置列表（支持按状态、规则Key和目标类型过滤）
     *
     * @param status     状态过滤（可选）
     * @param ruleKey    规则Key过滤（可选）
     * @param targetType 目标类型过滤（可选）
     * @return 灰度配置列表
     */
    public List<GrayscaleConfigResponse> listGrayscaleConfigs(String status, String ruleKey, String targetType) {
        List<GrayscaleConfig> configs;

        if (targetType != null && !targetType.isBlank()) {
            // 按目标类型过滤
            configs = grayscaleConfigRepository.findByTargetType(targetType);

            // 二次过滤
            if (ruleKey != null && !ruleKey.isBlank()) {
                configs = configs.stream()
                        .filter(c -> ruleKey.equals(c.getRuleKey()) || ruleKey.equals(c.getTargetKey()))
                        .collect(Collectors.toList());
            }
            if (status != null && !status.isBlank()) {
                GrayscaleStatus grayscaleStatus = GrayscaleStatus.valueOf(status);
                configs = configs.stream()
                        .filter(c -> grayscaleStatus.equals(c.getStatus()))
                        .collect(Collectors.toList());
            }
        } else if (ruleKey != null && !ruleKey.isBlank() && status != null && !status.isBlank()) {
            GrayscaleStatus grayscaleStatus = GrayscaleStatus.valueOf(status);
            configs = grayscaleConfigRepository
                    .findByRuleKeyAndStatusOrderByCreatedAtDesc(ruleKey, grayscaleStatus);
        } else if (ruleKey != null && !ruleKey.isBlank()) {
            configs = grayscaleConfigRepository.findByRuleKeyOrderByCreatedAtDesc(ruleKey);
        } else if (status != null && !status.isBlank()) {
            GrayscaleStatus grayscaleStatus = GrayscaleStatus.valueOf(status);
            configs = grayscaleConfigRepository.findByStatus(grayscaleStatus);
        } else {
            configs = grayscaleConfigRepository.findAllByOrderByCreatedAtDesc();
        }

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
