package com.eatmoreduck.ruleengine.decision.core

import com.eatmoreduck.ruleengine.decision.config.DecisionProperties
import com.eatmoreduck.ruleengine.decision.log.ExecutionLogService
import com.eatmoreduck.ruleengine.decision.metrics.DecisionMetrics
import com.eatmoreduck.ruleengine.domain.DecisionResult
import com.eatmoreduck.ruleengine.dsl.DslParser
import com.eatmoreduck.ruleengine.dsl.ParseResult
import com.eatmoreduck.ruleengine.engine.GroovyScriptEngine
import com.eatmoreduck.ruleengine.engine.ScriptEngineException
import com.eatmoreduck.ruleengine.engine.ScriptTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

/**
 * 决策主链路（同步）。
 *
 * 三个入口（契约与旧 DecisionController 一一对应）：
 * - [executeAdHoc]：POST /api/v1/decide，直传脚本的裸执行（不落库、不分流）；
 * - [decideByKey]：POST /api/v1/decide/{ruleKey}，cache-aware——版本钉住 + 灰度分流 + 快照缓存；
 * - [executeFlow]：POST /api/v1/decision-flows/{flowKey}/execute，流图解释执行。
 *
 * fail-safe 总纲（照搬旧实现）：任何解析失败、脚本异常、执行超时一律 REJECT，
 * 超时额外携带 timedOut 标记（[DecisionResult] 不变式：超时决策必须拒绝）。
 *
 * 可观测：decision.duration Timer（标签 target/action/version）+ decision.errors Counter。
 */
@Service
class DecisionService(
    private val grayscaleRouter: GrayscaleRouter,
    private val featureResolution: FeatureResolutionService,
    private val flowExecutor: FlowExecutor,
    private val scriptEngine: GroovyScriptEngine,
    private val executionLogService: ExecutionLogService,
    private val metrics: DecisionMetrics,
    private val properties: DecisionProperties,
) {
    private val log = LoggerFactory.getLogger(DecisionService::class.java)

    /**
     * 直传脚本决策（旧 executeDecision 语义）：不做灰度分流，脚本按请求原样执行。
     * 超时上限 = min(请求 timeoutMs, 配置 executionTimeoutMs)。
     */
    suspend fun executeAdHoc(
        ruleId: String,
        script: String,
        features: Map<String, Any?>?,
        requiredFeatures: List<String>?,
        timeoutMs: Long,
    ): DecisionResult {
        val startedAt = System.nanoTime()
        try {
            val resolved = featureResolution.resolve(features, requiredFeatures, featureTimeoutOf(timeoutMs))
            val raw =
                withContext(Dispatchers.IO) {
                    // 业务脚本契约以 features.<code> 访问特征（引擎按绑定变量注入，故以 "features" 键包裹）
                    scriptEngine.execute(script, mapOf(FEATURES_VARIABLE to resolved), Duration.ofMillis(executionBudgetMs(timeoutMs)))
                }
            return finalize(
                target = "script:$ruleId",
                version = null,
                features = resolved,
                result = ResultMapper.toDecision(raw, resolved),
                startedAt = startedAt,
            )
        } catch (e: TimeoutCancellationException) {
            return failSafeTimeout("script:$ruleId", features, startedAt)
        } catch (e: ScriptTimeoutException) {
            return failSafeTimeout("script:$ruleId", features, startedAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failSafeError("script:$ruleId", features, startedAt, e)
        }
    }

    /**
     * 按 ruleKey 决策（cache-aware 主链路）：
     * 版本钉住 → 灰度分流 → 特征解析 → 沙箱执行 → 结果映射 → 灰度/执行日志 → 指标。
     */
    suspend fun decideByKey(
        ruleKey: String,
        features: Map<String, Any?>?,
        requiredFeatures: List<String>?,
        timeoutMs: Long,
    ): DecisionResult {
        val startedAt = System.nanoTime()
        val requestFeatures = features ?: emptyMap()
        try {
            // 1. 版本钉住 + 灰度分流（快照缓存）
            val snapshot =
                when (val outcome = grayscaleRouter.resolveRule(ruleKey, requestFeatures)) {
                    is RuleSnapshotOutcome.NotFound -> return DecisionResult.rejected("规则不存在或未启用", executionTimeMs = elapsedMs(startedAt))
                    is RuleSnapshotOutcome.Disabled -> return DecisionResult.rejected("规则未启用或已删除", executionTimeMs = elapsedMs(startedAt))
                    is RuleSnapshotOutcome.Resolved -> outcome.snapshot
                }

            // 2. 特征解析（协程并发 + 超时降级）
            val resolved = featureResolution.resolve(requestFeatures, requiredFeatures, featureTimeoutOf(timeoutMs))

            // 3. 沙箱执行（任何异常 → fail-safe）
            val raw =
                withContext(Dispatchers.IO) {
                    scriptEngine.execute(
                        snapshot.definitionJson,
                        mapOf(FEATURES_VARIABLE to resolved),
                        Duration.ofMillis(executionBudgetMs(timeoutMs)),
                    )
                }
            val result = ResultMapper.toDecision(raw, resolved)

            // 4. 灰度执行日志（仅存在运行中灰度配置时记录，对齐旧时序）+ 执行日志 + 指标
            if (snapshot.grayscaleConfigId != null) {
                executionLogService.logCanary(
                    traceId = newTraceId(),
                    targetType = "RULE",
                    targetKey = ruleKey,
                    versionUsed = snapshot.pinnedVersion,
                    isCanary = snapshot.fromCanary,
                    requestFeatures = resolved,
                    decisionResult = result.action.name,
                    executionTimeMs = elapsedMs(startedAt),
                )
            }
            return finalize(ruleKey, snapshot.pinnedVersion, resolved, result, startedAt)
        } catch (e: TimeoutCancellationException) {
            return failSafeTimeout(ruleKey, requestFeatures, startedAt)
        } catch (e: ScriptTimeoutException) {
            return failSafeTimeout(ruleKey, requestFeatures, startedAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failSafeError(ruleKey, requestFeatures, startedAt, e)
        }
    }

    /**
     * 决策流执行：版本钉住 → 流图解析 → 解释执行。
     * 流灰度不计 grayscale_metrics（口径与旧一致），灰度执行日志记录钉住版本。
     */
    suspend fun executeFlow(
        flowKey: String,
        features: Map<String, Any?>?,
    ): DecisionResult {
        val startedAt = System.nanoTime()
        val requestFeatures = features ?: emptyMap()
        try {
            // 1. 版本钉住 + 灰度分流
            val snapshot =
                when (val outcome = grayscaleRouter.resolveFlow(flowKey, requestFeatures)) {
                    is FlowSnapshotOutcome.NotFound -> return DecisionResult.rejected("决策流不存在或未启用", executionTimeMs = elapsedMs(startedAt))
                    is FlowSnapshotOutcome.Resolved -> outcome.snapshot
                }

            // 2. 流图解析（失败 → fail-safe，旧实现落入整体 catch 的 REJECT）
            val graph =
                when (val parsed = DslParser.parseFlowGraph(snapshot.graphJson)) {
                    is ParseResult.Success -> {
                        parsed.value
                    }

                    is ParseResult.Failure -> {
                        throw IllegalStateException("流图解析失败: ${parsed.error.reason}")
                    }
                }

            // 3. 解释执行（规则集内单条规则按配置兜底超时）
            val result = flowExecutor.execute(graph, requestFeatures, properties.executionTimeoutMs)
            val final = result.copy(executionTimeMs = elapsedMs(startedAt))

            // 4. 灰度日志 + 执行日志 + 指标
            if (snapshot.grayscaleConfigId != null) {
                executionLogService.logCanary(
                    traceId = newTraceId(),
                    targetType = "DECISION_FLOW",
                    targetKey = flowKey,
                    versionUsed = snapshot.pinnedVersion,
                    isCanary = snapshot.fromCanary,
                    requestFeatures = requestFeatures,
                    decisionResult = final.action.name,
                    executionTimeMs = final.executionTimeMs,
                )
            }
            executionLogService.logSuccess(
                flowKey,
                snapshot.pinnedVersion,
                requestFeatures,
                final.action.name,
                final.reason,
                final.executionTimeMs,
            )
            metrics.recordDuration(flowKey, final.action.name, snapshot.pinnedVersion, final.executionTimeMs)
            return final
        } catch (e: CancellationException) {
            // 流执行失败的灰度错误日志（旧 asyncLogError 口径）仅在业务异常时记录
            if (e is TimeoutCancellationException) {
                return failSafeTimeout(flowKey, requestFeatures, startedAt)
            }
            throw e
        } catch (e: Exception) {
            executionLogService.logCanaryError(
                newTraceId(),
                "DECISION_FLOW",
                flowKey,
                0,
                false,
                requestFeatures,
                e.message ?: e.javaClass.simpleName,
                elapsedMs(startedAt),
            )
            return failSafeError(flowKey, requestFeatures, startedAt, e)
        }
    }

    // ---------- 内部 ----------

    /** 成功路径收尾：耗时回填、执行日志、耗时指标 */
    private fun finalize(
        target: String,
        version: Int?,
        features: Map<String, Any?>,
        result: DecisionResult,
        startedAt: Long,
    ): DecisionResult {
        val final = result.copy(executionTimeMs = elapsedMs(startedAt))
        executionLogService.logSuccess(target, version, features, final.action.name, final.reason, final.executionTimeMs)
        metrics.recordDuration(target, final.action.name, version ?: 0, final.executionTimeMs)
        return final
    }

    /** 整体执行预算（覆盖特征解析 + 脚本执行，即引擎侧中断阈值），语义对齐旧 future.get(request.timeoutMs) */
    private fun executionBudgetMs(timeoutMs: Long): Long {
        val requested = timeoutMs.takeIf { it > 0 } ?: DEFAULT_TIMEOUT_MS
        return minOf(requested, properties.executionTimeoutMs)
    }

    private fun featureTimeoutOf(timeoutMs: Long): Long = minOf(properties.featureTimeoutMs, executionBudgetMs(timeoutMs))

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    /** 超时 fail-safe：REJECT + timedOut（旧"规则执行超时"文案） */
    private suspend fun failSafeTimeout(
        target: String,
        features: Map<String, Any?>?,
        startedAt: Long,
    ): DecisionResult {
        val elapsed = elapsedMs(startedAt)
        log.warn("规则执行超时: target={}, {}ms", target, elapsed)
        metrics.recordError(target, "execute")
        executionLogService.logTimeout(target, null, features, elapsed)
        metrics.recordDuration(target, "REJECT", 0, elapsed)
        return DecisionResult.timedOut(elapsed, "规则执行超时")
    }

    /** 异常 fail-safe：REJECT（旧"规则执行失败: <msg>"文案） */
    private fun failSafeError(
        target: String,
        features: Map<String, Any?>?,
        startedAt: Long,
        e: Exception,
    ): DecisionResult {
        val elapsed = elapsedMs(startedAt)
        log.error("决策执行失败: target={}, {}ms", target, elapsed, e)
        metrics.recordError(target, "resolve")
        executionLogService.logError(target, null, features, elapsed, e.message)
        metrics.recordDuration(target, "REJECT", 0, elapsed)
        return DecisionResult.rejected("规则执行失败: ${e.message}", executionTimeMs = elapsed)
    }

    private fun newTraceId(): String = UUID.randomUUID().toString().replace("-", "")

    companion object {
        /** 请求未携带有效超时的兜底（契约默认 50ms） */
        private const val DEFAULT_TIMEOUT_MS = 50L

        /** 特征注入脚本的绑定变量名（业务脚本契约：features.<特征编码>） */
        const val FEATURES_VARIABLE = "features"
    }
}
