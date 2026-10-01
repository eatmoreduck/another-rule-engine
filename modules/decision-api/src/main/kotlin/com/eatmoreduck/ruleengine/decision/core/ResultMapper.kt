package com.eatmoreduck.ruleengine.decision.core

import com.eatmoreduck.ruleengine.domain.DecisionAction
import com.eatmoreduck.ruleengine.domain.DecisionResult

/**
 * 规则输出 → 领域决策结果的映射（语义照搬旧 buildDecisionResponse）：
 * - Boolean：true → PASS，false → REJECT，reason 固定"规则执行完成"；
 * - Map：取 `decision` / `reason` 字段（缺失 decision 按 fail-safe 归 REJECT）；
 * - String：原值即决策动作（经 [DecisionAction.fromEngineOutput] 归一化，无法识别归 REJECT）；
 * - 其他（含 null）：REJECT，reason 说明无效结果。
 */
object ResultMapper {
    fun toDecision(
        raw: Any?,
        executionContext: Map<String, Any?>,
    ): DecisionResult =
        when (raw) {
            is Boolean -> {
                if (raw) {
                    DecisionResult.passed(REASON_EXECUTED, executionTimeMs = 0, executionContext = executionContext)
                } else {
                    DecisionResult.rejected(REASON_EXECUTED, executionTimeMs = 0, executionContext = executionContext)
                }
            }

            is Map<*, *> -> {
                val decision = raw["decision"]?.toString()
                val reason = raw["reason"]?.toString()
                build(decision ?: "", reason ?: REASON_EXECUTED, executionContext)
            }

            is String -> {
                build(raw, REASON_EXECUTED, executionContext)
            }

            else -> {
                DecisionResult.rejected("规则返回无效结果: $raw", executionTimeMs = 0, executionContext = executionContext)
            }
        }

    /** 决策动作归一化：无法识别的输出按 fail-safe 语义归 REJECT（与旧契约 decision 大写口径一致） */
    private fun build(
        decisionRaw: String,
        reason: String,
        executionContext: Map<String, Any?>,
    ): DecisionResult {
        val action = DecisionAction.fromEngineOutput(decisionRaw)
        return DecisionResult(
            action = action,
            reason = reason,
            executionTimeMs = 0,
            executionContext = executionContext,
        )
    }

    private const val REASON_EXECUTED = "规则执行完成"
}
