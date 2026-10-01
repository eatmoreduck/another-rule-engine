package com.eatmoreduck.ruleengine.domain

/**
 * 决策结果值对象。
 *
 * 对应旧 DecisionResponse（decision / reason / executionTimeMs / timeout / executionContext）
 * 与 ExecutionLog 的输出列（outputDecision / outputReason / executionTimeMs / status / errorMessage）。
 *
 * 不变式：
 * - executionTimeMs >= 0
 * - timedOut=true 时动作必须是 REJECT（旧引擎超时兜底即拒绝）
 * - executionContext 对外只读
 */
data class DecisionResult(
    val action: DecisionAction,
    val reason: String? = null,
    val executionTimeMs: Long,
    val timedOut: Boolean = false,
    val executionContext: Map<String, Any?> = emptyMap(),
) {
    init {
        require(executionTimeMs >= 0) { "executionTimeMs 必须 >= 0，实际: $executionTimeMs" }
        if (timedOut) {
            check(action == DecisionAction.REJECT) { "超时决策的动作必须是 REJECT（fail-safe），实际: $action" }
        }
    }

    companion object {
        /** 放行 */
        fun passed(
            reason: String? = null,
            executionTimeMs: Long,
            executionContext: Map<String, Any?> = emptyMap(),
        ): DecisionResult = build(DecisionAction.PASS, reason, executionTimeMs, executionContext)

        /** 转人工审核 */
        fun manualReview(
            reason: String? = null,
            executionTimeMs: Long,
            executionContext: Map<String, Any?> = emptyMap(),
        ): DecisionResult = build(DecisionAction.MANUAL_REVIEW, reason, executionTimeMs, executionContext)

        /** 拒绝 */
        fun rejected(
            reason: String? = null,
            executionTimeMs: Long,
            executionContext: Map<String, Any?> = emptyMap(),
        ): DecisionResult = build(DecisionAction.REJECT, reason, executionTimeMs, executionContext)

        /**
         * 执行超时的兜底结果：动作固定 REJECT（fail-safe），timedOut=true。
         *
         * @param elapsedMs 超时发生时已消耗的时长
         */
        fun timedOut(
            elapsedMs: Long,
            reason: String? = null,
            executionContext: Map<String, Any?> = emptyMap(),
        ): DecisionResult = build(DecisionAction.REJECT, reason, elapsedMs, executionContext, timedOut = true)

        private fun build(
            action: DecisionAction,
            reason: String?,
            executionTimeMs: Long,
            executionContext: Map<String, Any?>,
            timedOut: Boolean = false,
        ): DecisionResult =
            DecisionResult(
                action = action,
                reason = reason,
                executionTimeMs = executionTimeMs,
                timedOut = timedOut,
                executionContext = executionContext,
            )
    }
}
