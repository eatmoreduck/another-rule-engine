package com.example.ruleengine.dsl

import tools.jackson.core.JacksonException
import kotlin.reflect.KClass

/**
 * 解析失败的原因描述。
 *
 * @param reason 一句话失败原因（取 Jackson 异常首行消息，中文语境可直接展示）
 * @param jsonLocation 出错位置（行:列），流级错误（非法 JSON）时提供
 * @param jsonPath Jackson 报告的属性路径引用（如 `RuleDefinition["rules"]->...`），结构错误时提供
 */
data class DslParseError(
    val reason: String,
    val jsonLocation: String? = null,
    val jsonPath: String? = null,
)

/**
 * 解析结果：成功携带模型，失败携带明确原因。
 *
 * 用法：
 * ```
 * when (val result = DslParser.parseRuleDefinition(json)) {
 *     is ParseResult.Success -> use(result.value)
 *     is ParseResult.Failure -> log(result.error.reason)
 * }
 * ```
 */
sealed interface ParseResult<out T> {
    /** 解析成功，携带反序列化后的模型 */
    data class Success<T>(
        val value: T,
    ) : ParseResult<T>

    /** 解析失败，携带错误描述 */
    data class Failure(
        val error: DslParseError,
    ) : ParseResult<Nothing>

    /** 成功时返回模型，否则返回 null */
    fun getOrNull(): T? =
        when (this) {
            is Success -> value
            is Failure -> null
        }

    /** 成功时返回模型，否则抛出 [IllegalStateException]（携带错误描述） */
    fun getOrThrow(): T =
        when (this) {
            is Success -> value
            is Failure -> throw IllegalStateException("DSL 解析失败: ${error.reason}")
        }
}

/**
 * 规则 DSL JSON 解析入口。
 *
 * 目标 JSON 契约与前端逐字对齐：
 * - [parseRuleDefinition] ← `FormRuleConfigV2`（表单模式规则定义树）
 * - [parseSingleRule] ← `SingleRuleConfig`（单规则模式）
 * - [parseFlowGraph] ← `{ nodes, edges }`（决策流图，React Flow 导出形状）
 */
object DslParser {
    /** 解析规则定义树（前端 FormRuleConfigV2） */
    fun parseRuleDefinition(json: String): ParseResult<RuleDefinition> = parse(json, RuleDefinition::class)

    /** 解析单规则配置（前端 SingleRuleConfig） */
    fun parseSingleRule(json: String): ParseResult<SingleRuleConfig> = parse(json, SingleRuleConfig::class)

    /** 解析决策流图（前端 `{ nodes, edges }`） */
    fun parseFlowGraph(json: String): ParseResult<FlowGraph> = parse(json, FlowGraph::class)

    /** 解析任意已建模类型 */
    fun <T : Any> parse(
        json: String,
        type: KClass<T>,
    ): ParseResult<T> =
        try {
            ParseResult.Success(DslJson.read(json, type.java))
        } catch (e: JacksonException) {
            // Jackson 3 异常均为非受检 RuntimeException，此处统一收敛为结果类型
            ParseResult.Failure(toError(e))
        } catch (e: IllegalArgumentException) {
            // 兜底：自定义绑定器（如 ThresholdValue.fromRaw）抛出的裸 IAE 若未被 Jackson 包装
            ParseResult.Failure(DslParseError(reason = e.message ?: "非法字段值"))
        }

    /** reified 便捷重载 */
    inline fun <reified T : Any> parse(json: String): ParseResult<T> = parse(json, T::class)

    /** 序列化回 JSON（供回写存储 / round-trip 使用） */
    fun write(value: Any): String = DslJson.write(value)

    /** 将 Jackson 异常收敛为可读的错误描述（首行消息 + 位置 + 路径） */
    internal fun toError(e: JacksonException): DslParseError {
        val reason =
            e.message
                ?.lineSequence()
                ?.firstOrNull()
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: e.javaClass.simpleName
        val location = e.location?.let { "${it.lineNr}:${it.columnNr}" }
        val path = e.pathReference?.takeUnless { it.isBlank() }
        return DslParseError(reason = reason, jsonLocation = location, jsonPath = path)
    }
}
