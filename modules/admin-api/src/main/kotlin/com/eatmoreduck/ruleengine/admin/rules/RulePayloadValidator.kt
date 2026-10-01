package com.eatmoreduck.ruleengine.admin.rules

import com.eatmoreduck.ruleengine.dsl.DslParser
import com.eatmoreduck.ruleengine.dsl.DslValidator
import com.eatmoreduck.ruleengine.dsl.ParseResult
import com.eatmoreduck.ruleengine.engine.GroovyScriptEngine
import com.eatmoreduck.ruleengine.engine.ValidationResult
import org.springframework.stereotype.Component

/** 规则定义载荷校验结果 */
sealed interface PayloadValidation {
    /** 校验通过 */
    data object Valid : PayloadValidation

    /** 校验失败，[detail] 为可读的失败原因（映射为旧契约错误响应） */
    data class Invalid(
        val detail: String,
    ) : PayloadValidation
}

/**
 * 规则定义载荷校验链（两道校验，按载荷形态自动分流）：
 *
 * 1. DSL 通道——载荷以 `{` 开头时，视为表单模式定义 JSON（前端 FormRuleConfigV2），
 *    经 modules/dsl 的 DslParser 解析 + DslValidator 结构校验；
 * 2. 脚本通道——其余载荷视为 Groovy 脚本文本（前端规则编辑器生成的脚本 / 历史数据），
 *    经 modules/engine 的 GroovyScriptEngine.validate() 做静态审计 + 沙箱编译校验。
 *
 * 两道校验的失败原因统一收敛为 [PayloadValidation.Invalid]，由服务层映射为
 * 旧契约的错误消息（"Groovy脚本语法错误: <detail>"）与 /rules/validate 的响应结构。
 */
@Component
class RulePayloadValidator(
    private val scriptEngine: GroovyScriptEngine,
) {
    private val dslValidator = DslValidator()

    fun validate(payload: String): PayloadValidation {
        val trimmed = payload.trim()
        if (trimmed.startsWith("{")) {
            return validateDslJson(trimmed)
        }
        return validateGroovyScript(payload)
    }

    /** DSL JSON 通道：解析失败或结构校验存在 ERROR 级问题即拒绝（WARNING 不阻断，运行时有兜底） */
    private fun validateDslJson(json: String): PayloadValidation =
        when (val parsed = DslParser.parseRuleDefinition(json)) {
            is ParseResult.Success -> {
                val result = dslValidator.validate(parsed.value)
                val errors = result.errors
                if (errors.isEmpty()) {
                    PayloadValidation.Valid
                } else {
                    PayloadValidation.Invalid(errors.joinToString("; ") { "${it.path}: ${it.message}" })
                }
            }

            is ParseResult.Failure -> {
                val location =
                    parsed.error.jsonLocation
                        ?.let { "（位置 $it）" }
                        .orEmpty()
                val path =
                    parsed.error.jsonPath
                        ?.let { "，路径 $it" }
                        .orEmpty()
                PayloadValidation.Invalid("${parsed.error.reason}$location$path")
            }
        }

    /** Groovy 脚本通道：静态审计 + 沙箱编译，不执行 */
    private fun validateGroovyScript(script: String): PayloadValidation =
        when (val result = scriptEngine.validate(script)) {
            is ValidationResult.Success -> {
                PayloadValidation.Valid
            }

            is ValidationResult.Failure -> {
                PayloadValidation.Invalid(result.errors.joinToString("; "))
            }
        }
}
