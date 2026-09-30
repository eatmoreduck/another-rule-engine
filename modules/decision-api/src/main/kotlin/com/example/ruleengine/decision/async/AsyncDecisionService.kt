package com.example.ruleengine.decision.async

import com.example.ruleengine.decision.config.ApplicationCoroutineScope
import com.example.ruleengine.decision.config.DecisionProperties
import com.example.ruleengine.decision.core.DecisionService
import com.example.ruleengine.domain.DecisionAction
import com.example.ruleengine.domain.DecisionResult
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 异步决策服务（REXEC-02，语义移植旧 AsyncRuleExecutionService.submitAsync）：
 * 提交即返回 requestId（立即 202），后台协程执行与同步链路一致的决策，
 * 结果写入 [AsyncResultStore] 供客户端轮询。
 *
 * 超时降级（旧 REXEC-05 的异步特有口径）：请求 timeoutMs 内未完成 →
 * PASS + "规则执行超时，返回默认通过决策" + timeout 标记；
 * 脚本内部异常 → REJECT（与同步链路 fail-safe 一致）。
 * 同步链路的引擎超时以 REJECT+timedOut 表达，此处据此改写为降级通过（旧语义对齐）。
 *
 * 协程友好：执行在应用级作用域的 IO 调度器上，不占用请求线程。
 */
@Service
class AsyncDecisionService(
    private val decisionService: DecisionService,
    private val resultStore: AsyncResultStore,
    private val scope: ApplicationCoroutineScope,
    private val properties: DecisionProperties,
) {
    private val log = LoggerFactory.getLogger(AsyncDecisionService::class.java)

    /**
     * 提交异步决策（"提交后轮询"模式）。
     *
     * @return requestId，轮询查询用的唯一标识
     */
    fun submit(
        ruleId: String,
        script: String?,
        features: Map<String, Any?>?,
        requiredFeatures: List<String>?,
        timeoutMs: Long,
    ): String {
        val requestId = UUID.randomUUID().toString()
        val budget = budgetOf(timeoutMs)
        log.info("提交异步决策请求: requestId={}, ruleId={}, timeoutMs={}", requestId, ruleId, budget)

        scope.launch(CoroutineName("async-decision")) {
            val startedAt = System.nanoTime()
            val outcome =
                try {
                    val result =
                        withContext(Dispatchers.IO) {
                            decisionService.executeAdHoc(
                                ruleId = ruleId,
                                script = script ?: "",
                                features = features,
                                requiredFeatures = requiredFeatures,
                                timeoutMs = budget,
                            )
                        }
                    if (result.timedOut) {
                        // 异步口径：超时降级为默认通过（旧 REXEC-05）
                        AsyncDecisionOutcome.from(DecisionAction.PASS, "规则执行超时，返回默认通过决策", result.executionTimeMs, timedOut = true)
                    } else {
                        fromDecision(result)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("异步决策执行失败: requestId={}, ruleId={}", requestId, ruleId, e)
                    AsyncDecisionOutcome.from(
                        DecisionAction.REJECT,
                        "规则执行失败: ${e.message}",
                        (System.nanoTime() - startedAt) / 1_000_000,
                    )
                }
            resultStore.storeResult(requestId, outcome)
            log.info("异步决策完成: requestId={}, decision={}, time={}ms", requestId, outcome.action.name, outcome.executionTimeMs)
        }
        return requestId
    }

    /** 查询异步结果；不存在（未完成或已过期）返回 null → 轮询响应 PROCESSING */
    fun get(requestId: String): AsyncDecisionOutcome? = resultStore.getResult(requestId)

    private fun budgetOf(timeoutMs: Long): Long {
        val requested = timeoutMs.takeIf { it > 0 } ?: DEFAULT_TIMEOUT_MS
        return minOf(requested, properties.executionTimeoutMs)
    }

    private fun fromDecision(result: DecisionResult): AsyncDecisionOutcome =
        AsyncDecisionOutcome.from(result.action, result.reason, result.executionTimeMs, result.timedOut)

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 50L
    }
}
