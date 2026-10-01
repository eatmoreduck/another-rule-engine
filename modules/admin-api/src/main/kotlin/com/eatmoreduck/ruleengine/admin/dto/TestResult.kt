package com.eatmoreduck.ruleengine.admin.dto

/**
 * 规则测试执行结果（字段集与旧 TestResult 逐字一致，前端 frontend/src/types/analytics.ts
 * 的 TestResult 接口是最终裁判）。
 *
 * 失败形态（规则不存在 / 脚本异常 / 超时）：仅 ruleKey / success / errorMessage /
 * executionTimeMs 有值，decision / reason / matchedConditions / executionContext 为 null，
 * HTTP 状态仍为 200（旧实现语义：测试失败是业务结果而非请求错误）。
 */
data class TestResult(
    val ruleKey: String,
    val decision: String?,
    val reason: String?,
    val executionTimeMs: Long,
    val success: Boolean,
    val errorMessage: String?,
    val matchedConditions: List<String>?,
    val executionContext: Map<String, Any?>?,
) {
    companion object {
        /** 成功执行：决策结果 + 命中条件快照 + 执行上下文回显 */
        fun success(
            ruleKey: String,
            decision: String,
            reason: String,
            executionTimeMs: Long,
            matchedConditions: List<String>,
            executionContext: Map<String, Any?>,
        ): TestResult =
            TestResult(
                ruleKey = ruleKey,
                decision = decision,
                reason = reason,
                executionTimeMs = executionTimeMs,
                success = true,
                errorMessage = null,
                matchedConditions = matchedConditions,
                executionContext = executionContext,
            )

        /** 失败执行：规则不存在 / 脚本异常 / 执行超时等 */
        fun failure(
            ruleKey: String,
            errorMessage: String,
            executionTimeMs: Long,
        ): TestResult =
            TestResult(
                ruleKey = ruleKey,
                decision = null,
                reason = null,
                executionTimeMs = executionTimeMs,
                success = false,
                errorMessage = errorMessage,
                matchedConditions = null,
                executionContext = null,
            )
    }
}
