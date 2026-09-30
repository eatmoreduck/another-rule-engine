package com.example.ruleengine.decision.dto

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

/**
 * 决策请求 DTO（与旧 DecisionRequest 契约逐字对齐）：
 * `{ruleId, script, features, requiredFeatures, timeoutMs}`。
 */
data class DecisionRequest(
    /** 规则业务键（旧字段名 ruleId，承载的实为 ruleKey） */
    @field:NotBlank(message = "规则ID不能为空")
    val ruleId: String = "",
    /** 规则脚本文本：POST /api/v1/decide 直传执行；按 ruleKey 决策时由服务端加载，不消费 */
    @field:NotNull
    val script: String? = null,
    val features: Map<String, Any?>? = null,
    val requiredFeatures: List<String>? = null,
    /** 请求级执行超时（毫秒），契约默认 50ms；与配置的兜底上限取小 */
    @field:Positive(message = "超时时间必须为正数")
    val timeoutMs: Long = 50,
)

/**
 * 决策响应 DTO（与旧 DecisionResponse 契约逐字对齐）：
 * `{decision, reason, executionTimeMs, timeout, executionContext}`。
 *
 * [executionContext] 为执行时实际生效的特征集合（旧实现回显），异常路径为 NULL
 * （与旧 builder 路径不设置该字段的行为一致）。
 */
data class DecisionResponse(
    val decision: String,
    val reason: String? = null,
    val executionTimeMs: Long,
    val timeout: Boolean = false,
    @field:JsonInclude(JsonInclude.Include.ALWAYS)
    val executionContext: Map<String, Any?>? = null,
)

/**
 * 标准错误响应（旧契约三字段结构，前端仅读取 message）。
 */
data class ErrorResponse(
    val code: Int,
    val message: String,
    val error: String,
) {
    companion object {
        fun of(
            status: Int,
            message: String,
        ): ErrorResponse = ErrorResponse(code = status, message = message, error = reasonPhrase(status))

        private fun reasonPhrase(status: Int): String =
            when (status) {
                400 -> "Bad Request"
                401 -> "Unauthorized"
                403 -> "Forbidden"
                404 -> "Not Found"
                405 -> "Method Not Allowed"
                415 -> "Unsupported Media Type"
                500 -> "Internal Server Error"
                else -> "Error"
            }
    }
}
