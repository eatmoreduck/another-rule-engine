package com.example.ruleengine.decision.core

import com.example.ruleengine.decision.config.DecisionProperties
import com.example.ruleengine.decision.repo.DecisionFlowReader
import com.example.ruleengine.decision.repo.GrayscaleMetricsCounter
import com.example.ruleengine.domain.GrayscaleRelease
import com.example.ruleengine.domain.GrayscaleTarget
import com.example.ruleengine.domain.GrayscaleTargetType
import com.example.ruleengine.domain.Rule
import com.example.ruleengine.domain.RuleStatus
import com.example.ruleengine.storage.repository.DecisionFlowMain
import com.example.ruleengine.storage.repository.GrayscaleReleaseRepository
import com.example.ruleengine.storage.repository.RuleRepository
import com.example.ruleengine.storage.repository.RuleVersionRepository
import com.github.benmanes.caffeine.cache.Caffeine
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.Optional

/**
 * 版本钉住 + 灰度分流路由器（决策热路径的快照事实源）。
 *
 * 一次决策的固定动作序列：
 * 1. 规则/流主行快照（Caffeine 短 TTL 缓存，未命中走仓储）；
 * 2. 运行中灰度配置（Caffeine 更短 TTL，策略值纯内存判定 [com.example.ruleengine.domain.GrayscaleRelease.isCanaryRequest]）；
 * 3. 灰度命中 → 钉住灰度版本；未命中/无灰度 → 钉住生效版本；
 * 4. 版本载荷（(key, version) 快照缓存，未命中走仓储）；
 * 5. 存在运行中灰度配置时递增 grayscale_metrics（SQL 端原子 UPDATE，口径与旧实现一致：
 *    命中灰度版本 → hit_count+1，恒 execution_count+1；执行前即记录，对齐旧时序）。
 *
 * 灰度一致性：分流判定与版本钉住在同一调用内完成，快照对象随后贯穿整次决策。
 *
 * fail-safe：任何仓储/缓存异常 → 回退生效版本（灰度判定异常 → 视为未命中），
 * 与旧 resolveGrayscaleScript 的 fallback 行为一致，绝不因灰度链路故障拒绝决策。
 */
@Component
class GrayscaleRouter(
    private val ruleRepository: RuleRepository,
    private val ruleVersionRepository: RuleVersionRepository,
    private val grayscaleReleaseRepository: GrayscaleReleaseRepository,
    private val decisionFlowReader: DecisionFlowReader,
    private val grayscaleMetrics: GrayscaleMetricsCounter,
    properties: DecisionProperties,
) : RuleSetPayloadSource {
    private val log = LoggerFactory.getLogger(GrayscaleRouter::class.java)

    /** 规则主行缓存：ruleKey → Rule（含停用/删除行，禁用语义由调用方区分） */
    private val ruleMainCache =
        Caffeine
            .newBuilder()
            .maximumSize(properties.mainCacheMaximumSize)
            .expireAfterWrite(Duration.ofSeconds(properties.snapshotExpireAfterWriteSeconds))
            .build<String, Rule>()

    /** 规则版本载荷缓存：(ruleKey, version) → definitionJson */
    private val ruleVersionCache =
        Caffeine
            .newBuilder()
            .maximumSize(properties.snapshotCacheMaximumSize)
            .expireAfterWrite(Duration.ofSeconds(properties.snapshotExpireAfterWriteSeconds))
            .build<VersionKey, String>()

    /** 运行中灰度配置缓存：目标 → 可空灰度聚合（Optional 承载 null，防止穿透打库） */
    private val grayscaleCache =
        Caffeine
            .newBuilder()
            .maximumSize(properties.mainCacheMaximumSize)
            .expireAfterWrite(Duration.ofSeconds(properties.grayscaleExpireAfterWriteSeconds))
            .build<GrayscaleTarget, Optional<GrayscaleRelease>>()

    /** 决策流主行缓存：flowKey → DecisionFlowMain */
    private val flowMainCache =
        Caffeine
            .newBuilder()
            .maximumSize(properties.mainCacheMaximumSize)
            .expireAfterWrite(Duration.ofSeconds(properties.snapshotExpireAfterWriteSeconds))
            .build<String, DecisionFlowMain>()

    /** 流图载荷缓存：(flowKey, version) → graphJson（含主表口径版本） */
    private val flowGraphCache =
        Caffeine
            .newBuilder()
            .maximumSize(properties.snapshotCacheMaximumSize)
            .expireAfterWrite(Duration.ofSeconds(properties.snapshotExpireAfterWriteSeconds))
            .build<VersionKey, String>()

    /**
     * 解析并钉住规则版本；[features] 用于灰度分流判定（纯内存）。
     * 规则不存在/停用/删除分别返回 [RuleSnapshotOutcome.NotFound] / [RuleSnapshotOutcome.Disabled]。
     */
    fun resolveRule(
        ruleKey: String,
        features: Map<String, Any?>,
    ): RuleSnapshotOutcome {
        val rule = loadRuleMain(ruleKey) ?: return RuleSnapshotOutcome.NotFound
        if (rule.status != RuleStatus.ENABLED) {
            return RuleSnapshotOutcome.Disabled
        }

        val running = runningRelease(GrayscaleTarget(GrayscaleTargetType.RULE, ruleKey))
        val canaryHit = running?.isCanaryRequest(features) == true

        // 灰度指标：存在运行中配置即记执行次数（执行前记录，对齐旧时序）；命中灰度版本记 hit
        if (running != null) {
            val hitVersion = if (canaryHit) running.grayscaleVersion else running.currentVersion
            recordMetricsQuietly(running.id, hitVersion)
        }

        if (canaryHit && running != null) {
            val canaryVersion = running.grayscaleVersion
            val canaryPayload = loadRuleVersion(ruleKey, canaryVersion)
            if (canaryPayload != null) {
                return RuleSnapshotOutcome.Resolved(
                    RuleSnapshot(ruleKey, canaryVersion, canaryPayload, fromCanary = true, grayscaleConfigId = running.id),
                )
            }
            // 灰度版本载荷缺失 → 回退生效版本（旧"灰度版本脚本加载失败 fallback"语义）
            log.warn("灰度版本载荷加载失败, 回退当前生效版本: ruleKey={}, version={}", ruleKey, canaryVersion)
        }

        val activeVersion = resolveActiveRuleVersion(ruleKey, rule)
        val payload =
            loadRuleVersion(ruleKey, activeVersion)
                ?: return RuleSnapshotOutcome.NotFound // 无任何版本载荷，视为规则不可执行
        return RuleSnapshotOutcome.Resolved(
            RuleSnapshot(ruleKey, activeVersion, payload, fromCanary = false, grayscaleConfigId = running?.id),
        )
    }

    /**
     * 解析并钉住决策流版本；流不存在或未启用返回 [FlowSnapshotOutcome.NotFound]。
     * 决策流灰度不计 grayscale_metrics（口径与旧 DecisionFlowExecutionService 一致，仅记 canary 日志）。
     */
    fun resolveFlow(
        flowKey: String,
        features: Map<String, Any?>,
    ): FlowSnapshotOutcome {
        val main = loadFlowMain(flowKey) ?: return FlowSnapshotOutcome.NotFound
        if (!main.enabled) {
            return FlowSnapshotOutcome.NotFound
        }

        val running = runningRelease(GrayscaleTarget(GrayscaleTargetType.DECISION_FLOW, flowKey))
        val canaryHit = running?.isCanaryRequest(features) == true

        if (canaryHit && running != null) {
            val canaryVersion = running.grayscaleVersion
            val canaryGraph = loadFlowGraph(flowKey, canaryVersion)
            if (canaryGraph != null) {
                return FlowSnapshotOutcome.Resolved(
                    FlowSnapshot(flowKey, canaryVersion, canaryGraph, fromCanary = true, grayscaleConfigId = running.id),
                )
            }
            log.warn("灰度版本 flowGraph 加载失败, 回退当前版本: flowKey={}, canaryVersion={}", flowKey, canaryVersion)
        }

        val pinnedVersion = main.activeVersion ?: main.version
        val graph =
            loadFlowGraph(flowKey, pinnedVersion)
                ?: return FlowSnapshotOutcome.NotFound
        return FlowSnapshotOutcome.Resolved(
            FlowSnapshot(flowKey, pinnedVersion, graph, fromCanary = false, grayscaleConfigId = running?.id),
        )
    }

    /** 规则集内引用规则的载荷（不走灰度，口径与旧规则集执行一致：主行生效版本） */
    override fun loadRulePayloadForRuleSet(ruleKey: String): String? {
        val rule = loadRuleMain(ruleKey) ?: return null
        if (rule.status != RuleStatus.ENABLED) {
            return null
        }
        return loadRuleVersion(ruleKey, resolveActiveRuleVersion(ruleKey, rule))
    }

    private fun resolveActiveRuleVersion(
        ruleKey: String,
        rule: Rule,
    ): Int {
        rule.activeVersion?.let { return it }
        // 历史数据主行无 activeVersion 指针 → 查 ACTIVE 版本行；再缺省回退最新内容版本
        return transaction { ruleVersionRepository.findActiveVersion(ruleKey)?.version }
            ?: rule.currentVersion
    }

    /** 缓存优先的规则版本载荷（getIfPresent+put 模式：Caffeine 的 Kotlin 泛型不支持可空 mapping 结果） */
    private fun loadRuleVersion(
        ruleKey: String,
        version: Int,
    ): String? {
        val key = VersionKey(ruleKey, version)
        ruleVersionCache.getIfPresent(key)?.let { return it }
        val payload = transaction { ruleVersionRepository.findByRuleKeyAndVersion(ruleKey, version)?.definitionJson }
        if (payload != null) {
            ruleVersionCache.put(key, payload)
        }
        return payload
    }

    /** 缓存优先的流图载荷（含"版本历史缺失回退主表行"链路） */
    private fun loadFlowGraph(
        flowKey: String,
        version: Int,
    ): String? {
        val key = VersionKey(flowKey, version)
        flowGraphCache.getIfPresent(key)?.let { return it }
        val graph =
            transaction {
                decisionFlowReader.findVersion(flowKey, version)?.flowGraph
                    // 版本历史缺失（如灰度配置指向的版本行被清理）→ 回退主表行
                    ?: decisionFlowReader
                        .findMain(flowKey)
                        ?.takeIf { it.activeVersion == version || it.version == version }
                        ?.flowGraph
            }
        if (graph != null) {
            flowGraphCache.put(key, graph)
        }
        return graph
    }

    /** 缓存优先的运行中灰度配置（Optional 包装空值防穿透打库） */
    private fun runningRelease(target: GrayscaleTarget): GrayscaleRelease? =
        try {
            val cached = grayscaleCache.getIfPresent(target)
            if (cached != null) {
                cached.orElse(null)
            } else {
                val release = transaction { grayscaleReleaseRepository.findRunningByTarget(target) }
                grayscaleCache.put(target, Optional.ofNullable(release))
                release
            }
        } catch (e: Exception) {
            // 灰度链路故障不阻塞决策：视为无灰度，钉住生效版本
            log.error("灰度配置加载异常, 视为无灰度: target={}", target, e)
            null
        }

    private fun recordMetricsQuietly(
        configId: Long?,
        version: Int,
    ) {
        if (configId == null) return
        try {
            val updated = grayscaleMetrics.increment(configId, version, execTimeMs = 0, isSuccess = true)
            if (updated == 0) {
                log.warn("灰度指标记录不存在: configId={}, version={}", configId, version)
            }
        } catch (e: Exception) {
            log.warn("记录灰度指标失败: configId={}, version={}", configId, version, e)
        }
    }

    /** 缓存优先的规则主行 */
    private fun loadRuleMain(ruleKey: String): Rule? {
        ruleMainCache.getIfPresent(ruleKey)?.let { return it }
        val rule = transaction { ruleRepository.findByRuleKey(ruleKey) }
        if (rule != null) {
            ruleMainCache.put(ruleKey, rule)
        }
        return rule
    }

    /** 缓存优先的决策流主行 */
    private fun loadFlowMain(flowKey: String): DecisionFlowMain? {
        flowMainCache.getIfPresent(flowKey)?.let { return it }
        val main = transaction { decisionFlowReader.findMain(flowKey) }
        if (main != null) {
            flowMainCache.put(flowKey, main)
        }
        return main
    }

    private data class VersionKey(
        val key: String,
        val version: Int,
    )

    // ---------- 缓存失效入口（阶段 5：Redis pub/sub 订阅侧调用，替代 TTL 兜底的最长 30s 延迟） ----------

    /**
     * 失效规则相关缓存：主行 + 版本载荷两层。
     * [ruleKey] 为 null 时整层失效（全量兜底口径）；否则按键精确失效。
     */
    fun invalidateRule(ruleKey: String?) {
        if (ruleKey == null) {
            ruleMainCache.invalidateAll()
            ruleVersionCache.invalidateAll()
        } else {
            ruleMainCache.invalidate(ruleKey)
            ruleVersionCache.asMap().keys.removeIf { it.key == ruleKey }
        }
    }

    /** 失效决策流相关缓存：主行 + 流图载荷两层（口径同 [invalidateRule]） */
    fun invalidateFlow(flowKey: String?) {
        if (flowKey == null) {
            flowMainCache.invalidateAll()
            flowGraphCache.invalidateAll()
        } else {
            flowMainCache.invalidate(flowKey)
            flowGraphCache.asMap().keys.removeIf { it.key == flowKey }
        }
    }

    /**
     * 失效运行中灰度配置缓存（含 Optional 负缓存条目，防止"已停止灰度"的旧值滞留）。
     * [targetKey] 为 null 时整层失效；灰度键在规则与决策流两类目标间共享 key 空间，按 key 匹配即可。
     */
    fun invalidateGrayscale(targetKey: String?) {
        if (targetKey == null) {
            grayscaleCache.invalidateAll()
        } else {
            grayscaleCache.asMap().keys.removeIf { it.key == targetKey }
        }
    }

    /** 各层缓存条目数快照（可观测性 + 失效链路测试断言；先 cleanUp 强制结算挂起写入，读数确定） */
    fun cacheEntryCounts(): Map<String, Long> {
        ruleMainCache.cleanUp()
        ruleVersionCache.cleanUp()
        grayscaleCache.cleanUp()
        flowMainCache.cleanUp()
        flowGraphCache.cleanUp()
        return mapOf(
            "ruleMain" to ruleMainCache.estimatedSize(),
            "ruleVersion" to ruleVersionCache.estimatedSize(),
            "grayscale" to grayscaleCache.estimatedSize(),
            "flowMain" to flowMainCache.estimatedSize(),
            "flowGraph" to flowGraphCache.estimatedSize(),
        )
    }
}
