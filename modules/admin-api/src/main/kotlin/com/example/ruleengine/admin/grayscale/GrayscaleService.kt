package com.example.ruleengine.admin.grayscale

import com.example.ruleengine.admin.cache.publishInvalidation
import com.example.ruleengine.admin.dto.CreateGrayscaleRequest
import com.example.ruleengine.admin.dto.GrayscaleConfigResponse
import com.example.ruleengine.admin.dto.GrayscaleReportResponse
import com.example.ruleengine.domain.FeatureCondition
import com.example.ruleengine.domain.FeatureOperator
import com.example.ruleengine.domain.GrayscalePolicy
import com.example.ruleengine.domain.GrayscaleRelease
import com.example.ruleengine.domain.GrayscaleStatus
import com.example.ruleengine.domain.GrayscaleTarget
import com.example.ruleengine.domain.GrayscaleTargetType
import com.example.ruleengine.domain.IllegalTransitionException
import com.example.ruleengine.domain.VersionStatus
import com.example.ruleengine.shared.cache.CacheInvalidationType
import com.example.ruleengine.storage.repository.GrayscaleReleaseRepository
import com.example.ruleengine.storage.repository.RuleRepository
import com.example.ruleengine.storage.repository.RuleVersionRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.Instant

/**
 * 灰度发布服务：创建 / 启动 / 暂停 / 全量切换 / 回滚 / 列表 / 对比报告。
 *
 * 业务规则照搬旧 GrayscaleService（守卫消息逐字一致），行为经阶段 1 领域模型修正：
 * - 策略参数收敛为领域 [GrayscalePolicy]（FEATURE 缺失/非法、WHITELIST 空名单在创建时
 *   即拒绝为 400——旧实现允许落库坏配置，运行时"永不命中"静默失效）；
 * - 灰度版本不得与现行生效版本相同（领域不变式，旧实现允许同版本灰度）；
 * - 启动灰度时灰度版本状态真正推进为 CANARY（旧实现 status 列从不流转）；
 * - 全量切换经版本发布守卫（只有 DRAFT/CANARY 可发布），旧 ACTIVE 版本归档，
 *   规则主表指针推进到灰度版本；
 * - 状态守卫失败统一映射为 400（旧实现 IllegalStateException 落入兜底 500，
 *   按本批任务约定收敛为 400 类错误响应，消息不变）。
 */
@Service
class GrayscaleService(
    private val releaseRepository: GrayscaleReleaseRepository,
    private val ruleRepository: RuleRepository,
    private val versionRepository: RuleVersionRepository,
    private val decisionFlowSupportRepository: DecisionFlowSupportRepository,
    private val metricsRepository: GrayscaleMetricsRepository,
    private val assembler: GrayscaleAssembler,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * 创建灰度配置（DRAFT），并初始化当前/灰度两个版本的零值指标。
     */
    @Transactional
    fun createGrayscaleConfig(
        request: CreateGrayscaleRequest,
        operator: String,
    ): GrayscaleConfigResponse {
        val targetKey = request.targetKey?.takeIf { it.isNotBlank() } ?: request.ruleKey
        if (targetKey.isNullOrBlank()) {
            throw IllegalArgumentException("目标 Key 不能为空（ruleKey 或 targetKey）")
        }
        val targetType = parseTargetType(request.targetType)
        val target = GrayscaleTarget(targetType, targetKey)
        val grayscaleVersion = request.grayscaleVersion ?: throw IllegalArgumentException("灰度版本号不能为空")
        val percentage = request.grayscalePercentage ?: throw IllegalArgumentException("灰度百分比不能为空")

        log.info(
            "创建灰度配置: targetType={}, targetKey={}, grayscaleVersion={}, percentage={}, strategy={}, operator={}",
            targetType,
            targetKey,
            grayscaleVersion,
            percentage,
            request.strategyType,
            operator,
        )

        val currentVersion =
            when (targetType) {
                GrayscaleTargetType.DECISION_FLOW -> {
                    val flow =
                        decisionFlowSupportRepository.findFlowMain(targetKey)
                            ?: throw IllegalArgumentException("决策流不存在: $targetKey")
                    if (releaseRepository.findRunningByTarget(target) != null) {
                        throw IllegalArgumentException("决策流已有运行中的灰度配置: $targetKey")
                    }
                    decisionFlowSupportRepository.findFlowVersion(targetKey, grayscaleVersion)
                        ?: throw IllegalArgumentException(
                            "决策流灰度版本不存在: $targetKey version=$grayscaleVersion",
                        )
                    flow.activeVersion ?: flow.version
                }

                GrayscaleTargetType.RULE -> {
                    val rule =
                        ruleRepository.findByRuleKey(targetKey)
                            ?: throw IllegalArgumentException("规则不存在: $targetKey")
                    if (releaseRepository.findRunningByTarget(target) != null) {
                        throw IllegalArgumentException("规则已有运行中的灰度配置: $targetKey")
                    }
                    versionRepository.findByRuleKeyAndVersion(targetKey, grayscaleVersion)
                        ?: throw IllegalArgumentException("灰度版本不存在: $targetKey version=$grayscaleVersion")
                    rule.activeVersion ?: rule.currentVersion
                }
            }

        val release =
            GrayscaleRelease(
                target = target,
                currentVersion = currentVersion,
                grayscaleVersion = grayscaleVersion,
                policy = decodePolicy(request, percentage),
                status = GrayscaleStatus.DRAFT,
                dualRunEnabled = request.dualRunEnabled,
                description = request.description,
                createdBy = operator,
                createdAt = Instant.now(),
            )
        val saved = releaseRepository.save(release)
        metricsRepository.initMetrics(saved.id!!, currentVersion, grayscaleVersion)

        log.info("灰度配置创建成功: id={}, targetType={}, targetKey={}", saved.id, targetType, targetKey)
        eventPublisher.publishInvalidation(CacheInvalidationType.GRAYSCALE, targetKey)
        return assembler.toResponse(saved)
    }

    /** 启动灰度（DRAFT/PAUSED → RUNNING），灰度版本推进为 CANARY */
    @Transactional
    fun startGrayscale(configId: Long): GrayscaleConfigResponse {
        log.info("启动灰度: configId={}", configId)
        val config =
            releaseRepository.findById(configId)
                ?: throw IllegalArgumentException("灰度配置不存在: $configId")
        if (config.status != GrayscaleStatus.DRAFT && config.status != GrayscaleStatus.PAUSED) {
            throw IllegalArgumentException(
                "只有草稿或已暂停状态的灰度配置才能启动，当前状态: ${statusDescription(config.status)}",
            )
        }
        val saved = releaseRepository.save(config.start(Instant.now()))
        markGrayscaleVersionCanary(saved)
        log.info("灰度已启动: configId={}, targetKey={}", configId, saved.target.key)
        eventPublisher.publishInvalidation(CacheInvalidationType.GRAYSCALE, saved.target.key)
        return assembler.toResponse(saved)
    }

    /** 暂停灰度（RUNNING → PAUSED） */
    @Transactional
    fun pauseGrayscale(configId: Long): GrayscaleConfigResponse {
        log.info("暂停灰度: configId={}", configId)
        val config =
            releaseRepository.findById(configId)
                ?: throw IllegalArgumentException("灰度配置不存在: $configId")
        if (config.status != GrayscaleStatus.RUNNING) {
            throw IllegalArgumentException(
                "只有运行中的灰度配置才能暂停，当前状态: ${statusDescription(config.status)}",
            )
        }
        val saved = releaseRepository.save(config.pause())
        log.info("灰度已暂停: configId={}", configId)
        eventPublisher.publishInvalidation(CacheInvalidationType.GRAYSCALE, saved.target.key)
        return assembler.toResponse(saved)
    }

    /**
     * 完成灰度（全量切换）：灰度版本发布为 ACTIVE、旧 ACTIVE 归档、目标主指针推进。
     */
    @Transactional
    fun completeGrayscale(configId: Long): GrayscaleConfigResponse {
        log.info("完成灰度（全量切换）: configId={}", configId)
        val config =
            releaseRepository.findById(configId)
                ?: throw IllegalArgumentException("灰度配置不存在: $configId")
        if (config.status != GrayscaleStatus.RUNNING && config.status != GrayscaleStatus.PAUSED) {
            throw IllegalArgumentException(
                "只有运行中或已暂停的灰度配置才能完成，当前状态: ${statusDescription(config.status)}",
            )
        }

        val targetKey = config.target.key
        val grayscaleVersion = config.grayscaleVersion
        when (config.target.type) {
            GrayscaleTargetType.RULE -> {
                publishRuleVersion(targetKey, grayscaleVersion, config.createdBy)
            }

            GrayscaleTargetType.DECISION_FLOW -> {
                val row =
                    decisionFlowSupportRepository.findFlowVersion(targetKey, grayscaleVersion)
                        ?: throw IllegalArgumentException("决策流灰度版本不存在: version=$grayscaleVersion")
                decisionFlowSupportRepository.switchFlowVersion(targetKey, grayscaleVersion, row.flowGraph)
            }
        }

        val saved = releaseRepository.save(config.complete(Instant.now()))
        log.info(
            "灰度已完成，全量切换到版本 {}: configId={}, targetType={}",
            saved.grayscaleVersion,
            configId,
            saved.target.type.name,
        )
        // 全量切换推进了目标主指针（规则生效版本 / 决策流生效版本），除灰度缓存外还需失效对应载荷缓存
        eventPublisher.publishInvalidation(CacheInvalidationType.GRAYSCALE, saved.target.key)
        when (saved.target.type) {
            GrayscaleTargetType.RULE -> eventPublisher.publishInvalidation(CacheInvalidationType.RULE, saved.target.key)
            GrayscaleTargetType.DECISION_FLOW -> eventPublisher.publishInvalidation(CacheInvalidationType.FLOW, saved.target.key)
        }
        return assembler.toResponse(saved)
    }

    /** 回滚灰度（DRAFT/RUNNING/PAUSED → ROLLED_BACK；目标未被切换，无需恢复内容） */
    @Transactional
    fun rollbackGrayscale(configId: Long): GrayscaleConfigResponse {
        log.info("回滚灰度: configId={}", configId)
        val config =
            releaseRepository.findById(configId)
                ?: throw IllegalArgumentException("灰度配置不存在: $configId")
        if (config.status == GrayscaleStatus.COMPLETED || config.status == GrayscaleStatus.ROLLED_BACK) {
            throw IllegalArgumentException(
                "已完成或已回滚的灰度配置不能再次回滚，当前状态: ${statusDescription(config.status)}",
            )
        }
        val saved =
            try {
                releaseRepository.save(config.rollback(Instant.now()))
            } catch (e: IllegalTransitionException) {
                throw IllegalArgumentException(e.message, e)
            }
        log.info("灰度已回滚: configId={}", configId)
        // 回滚后运行中配置消失：决策侧灰度缓存（含负缓存）必须失效，防止"已停止的灰度仍分流"
        eventPublisher.publishInvalidation(CacheInvalidationType.GRAYSCALE, saved.target.key)
        return assembler.toResponse(saved)
    }

    /** 目标（规则）当前生效版本号——决策分流使用，此查询保留给后续阶段复用 */
    @Transactional(readOnly = true)
    fun getRunningConfig(ruleKey: String): GrayscaleRelease? =
        releaseRepository.findRunningByTarget(GrayscaleTarget(GrayscaleTargetType.RULE, ruleKey))

    /** 灰度对比报告（指标行由创建时初始化，执行计数由决策侧写入） */
    @Transactional(readOnly = true)
    fun getGrayscaleReport(configId: Long): GrayscaleReportResponse {
        log.info("获取灰度对比报告: configId={}", configId)
        val config =
            releaseRepository.findById(configId)
                ?: throw IllegalArgumentException("灰度配置不存在: $configId")
        val metrics = metricsRepository.findByConfigId(configId)
        return GrayscaleReportResponse(
            configId = config.id,
            ruleKey = config.target.key,
            currentVersion = config.currentVersion,
            grayscaleVersion = config.grayscaleVersion,
            grayscalePercentage = assembler.toResponse(config).grayscalePercentage,
            currentVersionMetrics = buildVersionMetrics(config.currentVersion, metrics),
            grayscaleVersionMetrics = buildVersionMetrics(config.grayscaleVersion, metrics),
        )
    }

    /** 规则的全部灰度配置（含决策流目标的历史兼容列命中），按创建时间降序 */
    @Transactional(readOnly = true)
    fun getGrayscaleConfigs(ruleKey: String): List<GrayscaleConfigResponse> =
        releaseRepository
            .findAll()
            .filter { it.target.key == ruleKey }
            .map(assembler::toResponse)

    /**
     * 查询灰度配置列表（状态 / 规则Key / 目标类型过滤，过滤优先级照搬旧实现）。
     */
    @Transactional(readOnly = true)
    fun listGrayscaleConfigs(
        status: String?,
        ruleKey: String?,
        targetType: String?,
    ): List<GrayscaleConfigResponse> {
        val statusEnum = parseStatus(status)
        val targetTypeEnum = parseTargetTypeOrNull(targetType)
        var configs =
            when {
                targetTypeEnum != null -> {
                    releaseRepository.findAll().filter { it.target.type == targetTypeEnum }
                }

                !ruleKey.isNullOrBlank() && statusEnum != null -> {
                    releaseRepository
                        .findByStatus(statusEnum)
                        .filter { it.target.key == ruleKey }
                }

                !ruleKey.isNullOrBlank() -> {
                    releaseRepository.findAll().filter { it.target.key == ruleKey }
                }

                statusEnum != null -> {
                    releaseRepository.findByStatus(statusEnum)
                }

                else -> {
                    releaseRepository.findAll()
                }
            }
        // 与旧实现一致的二次过滤（targetType 与其他条件组合时）
        if (targetTypeEnum != null) {
            if (!ruleKey.isNullOrBlank()) {
                configs = configs.filter { it.target.key == ruleKey }
            }
            if (statusEnum != null) {
                configs = configs.filter { it.status == statusEnum }
            }
        }
        return configs.map(assembler::toResponse)
    }

    // ---------- 私有辅助 ----------

    /** 规则灰度全量切换：发布守卫 + 旧 ACTIVE 归档 + 主表指针推进 */
    private fun publishRuleVersion(
        ruleKey: String,
        grayscaleVersion: Int,
        operator: String,
    ) {
        val rule =
            ruleRepository.findByRuleKey(ruleKey)
                ?: throw IllegalArgumentException("规则不存在: $ruleKey")
        val targetRow =
            versionRepository.findByRuleKeyAndVersion(ruleKey, grayscaleVersion)
                ?: throw IllegalArgumentException("灰度版本不存在: version=$grayscaleVersion")
        if (!targetRow.status.publishable) {
            throw IllegalArgumentException(
                "只有草稿或灰度中的版本才能发布，当前状态: ${targetRow.status.name}",
            )
        }
        versionRepository.findByStatus(ruleKey, VersionStatus.ACTIVE).forEach { active ->
            versionRepository.save(active.archive())
        }
        versionRepository.save(targetRow.publish())
        ruleRepository.save(rule.activateVersion(grayscaleVersion, operator, Instant.now()))
        log.info("发布规则版本: ruleKey={}, version={}, operator={}", ruleKey, grayscaleVersion, operator)
    }

    /** 阶段 1 修正：灰度启动时灰度版本状态真正推进为 CANARY（幂等，已 CANARY 跳过） */
    private fun markGrayscaleVersionCanary(config: GrayscaleRelease) {
        when (config.target.type) {
            GrayscaleTargetType.RULE -> {
                val version =
                    versionRepository.findByRuleKeyAndVersion(config.target.key, config.grayscaleVersion)
                        ?: return
                if (version.status == VersionStatus.DRAFT) {
                    versionRepository.save(version.startCanary())
                }
            }

            GrayscaleTargetType.DECISION_FLOW -> {
                val version =
                    decisionFlowSupportRepository.findFlowVersion(config.target.key, config.grayscaleVersion)
                        ?: return
                if (version.status == null || version.status == "DRAFT") {
                    decisionFlowSupportRepository.markFlowVersionCanary(config.target.key, config.grayscaleVersion)
                }
            }
        }
    }

    private fun statusDescription(status: GrayscaleStatus): String =
        when (status) {
            GrayscaleStatus.DRAFT -> "草稿"
            GrayscaleStatus.RUNNING -> "运行中"
            GrayscaleStatus.PAUSED -> "已暂停"
            GrayscaleStatus.COMPLETED -> "已完成"
            GrayscaleStatus.ROLLED_BACK -> "已回滚"
        }

    private fun parseTargetType(raw: String?): GrayscaleTargetType =
        when (raw?.trim()?.uppercase()) {
            null, "", "RULE" -> GrayscaleTargetType.RULE
            "DECISION_FLOW" -> GrayscaleTargetType.DECISION_FLOW
            else -> throw IllegalArgumentException("未知的灰度目标类型: $raw")
        }

    private fun parseTargetTypeOrNull(raw: String?): GrayscaleTargetType? =
        raw
            ?.takeIf { it.isNotBlank() }
            ?.let { parseTargetType(it) }

    private fun parseStatus(raw: String?): GrayscaleStatus? {
        val value = raw?.takeIf { it.isNotBlank() } ?: return null
        return try {
            GrayscaleStatus.valueOf(value.trim().uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("未知的灰度状态: $value")
        }
    }

    /** 请求字段 → 领域策略（非法组合在创建时即拒绝为 400，不落库坏配置） */
    private fun decodePolicy(
        request: CreateGrayscaleRequest,
        percentage: Int,
    ): GrayscalePolicy =
        when (request.strategyType?.trim()?.uppercase()) {
            null, "", "PERCENTAGE" -> {
                GrayscalePolicy.Percentage(percentage)
            }

            "WHITELIST" -> {
                val ids =
                    request.whitelistIds
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        .orEmpty()
                if (ids.isEmpty()) {
                    throw IllegalArgumentException("白名单不能为空（WHITELIST 策略必须提供至少一个用户ID）")
                }
                GrayscalePolicy.Whitelist(ids.toSet())
            }

            "FEATURE" -> {
                val raw =
                    request.featureRules?.takeIf { it.isNotBlank() }
                        ?: throw IllegalArgumentException("特征匹配规则不能为空（FEATURE 策略必须提供 featureRules）")
                val conditions =
                    try {
                        mapper
                            .readValue(raw, Array<ConditionJson>::class.java)
                            .map { json ->
                                FeatureCondition(
                                    field = json.field,
                                    operator = decodeOperator(json.operator),
                                    value = json.value,
                                )
                            }
                    } catch (e: JacksonException) {
                        throw IllegalArgumentException("feature_rules JSON 无法解析: $raw", e)
                    }
                if (conditions.isEmpty()) {
                    throw IllegalArgumentException("feature_rules 为空数组（空条件集属于配置错误）")
                }
                GrayscalePolicy.Feature(conditions)
            }

            else -> {
                throw IllegalArgumentException("未知的灰度策略类型: ${request.strategyType}")
            }
        }

    private fun decodeOperator(operator: String?): FeatureOperator {
        val value =
            operator?.trim()?.uppercase().takeUnless { it.isNullOrEmpty() }
                ?: throw IllegalArgumentException("feature_rules 条件缺少 operator")
        return try {
            FeatureOperator.valueOf(value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("feature_rules 携带未知 operator: $operator")
        }
    }

    /** 灰度报告版本指标（口径照搬旧 buildVersionMetrics：无指标行时回零值） */
    private fun buildVersionMetrics(
        version: Int,
        metrics: List<GrayscaleMetricRow>,
    ): GrayscaleReportResponse.VersionMetrics {
        val metric = metrics.firstOrNull { it.version == version }
        if (metric == null) {
            return GrayscaleReportResponse.VersionMetrics(
                version = version,
                executionCount = 0,
                hitCount = 0,
                errorCount = 0,
                avgExecutionTimeMs = 0,
                errorRate = 0.0,
                hitRate = 0.0,
            )
        }
        val errorRate =
            if (metric.executionCount > 0) metric.errorCount.toDouble() / metric.executionCount * 100 else 0.0
        val hitRate =
            if (metric.executionCount > 0) metric.hitCount.toDouble() / metric.executionCount * 100 else 0.0
        return GrayscaleReportResponse.VersionMetrics(
            version = metric.version,
            executionCount = metric.executionCount,
            hitCount = metric.hitCount,
            errorCount = metric.errorCount,
            avgExecutionTimeMs = metric.avgExecutionTimeMs,
            errorRate = Math.round(errorRate * 100.0) / 100.0,
            hitRate = Math.round(hitRate * 100.0) / 100.0,
        )
    }

    /** feature_rules JSON 单条条件（与旧格式 `{"field","operator","value"}` 一致） */
    private data class ConditionJson(
        val field: String,
        val operator: String,
        val value: String? = null,
    )

    companion object {
        private val log = LoggerFactory.getLogger(GrayscaleService::class.java)

        private val mapper: ObjectMapper =
            JsonMapper
                .builder()
                .addModule(KotlinModule.Builder().build())
                .build()
    }
}
