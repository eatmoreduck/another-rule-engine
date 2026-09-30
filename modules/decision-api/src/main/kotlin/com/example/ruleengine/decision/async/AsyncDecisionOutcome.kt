package com.example.ruleengine.decision.async

import com.example.ruleengine.domain.DecisionAction

/**
 * 异步决策的存储形态（对应旧 DecisionResponse 的轮询回显字段）。
 *
 * 不复用 [com.example.ruleengine.domain.DecisionResult] 的原因：旧异步链路的超时降级
 * 语义为 PASS + timeout=true（REXEC-05"超时返回默认通过决策"），与领域不变式
 * "timedOut 必须拒绝"冲突；异步降级是业务策略而非引擎 fail-safe，故独立建模不继承该约束。
 */
data class AsyncDecisionOutcome(
    val action: DecisionAction,
    val reason: String?,
    val executionTimeMs: Long,
    val timedOut: Boolean,
) {
    companion object {
        fun from(
            action: DecisionAction,
            reason: String?,
            executionTimeMs: Long,
            timedOut: Boolean = false,
        ): AsyncDecisionOutcome = AsyncDecisionOutcome(action, reason, executionTimeMs, timedOut)
    }
}
