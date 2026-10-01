package com.eatmoreduck.ruleengine.admin.flows

import com.eatmoreduck.ruleengine.dsl.DslParser
import com.eatmoreduck.ruleengine.dsl.DslValidator
import com.eatmoreduck.ruleengine.dsl.FlowGraph
import com.eatmoreduck.ruleengine.dsl.ParseResult
import org.springframework.stereotype.Component

/** 决策流图载荷校验结果 */
sealed interface FlowGraphValidation {
    /** 校验通过，携带解析后的图模型（供调用方复用） */
    data class Valid(
        val graph: FlowGraph,
    ) : FlowGraphValidation

    /** 校验失败，[detail] 为可读的失败原因（映射为旧契约错误响应） */
    data class Invalid(
        val detail: String,
    ) : FlowGraphValidation
}

/**
 * 决策流图载荷校验链（保存与发布前的统一守卫）：
 *
 * 1. 解析——modules/dsl 的 DslParser.parseFlowGraph 解析 `{ nodes, edges }`
 *    （前端 React Flow 导出形状），JSON 非法即拒绝；
 * 2. 结构——DslValidator.validate 做图结构校验（唯一 start / 边引用完整 /
 *    条件节点字段完整等），存在 ERROR 级问题即拒绝（WARNING 不阻断，运行时有兜底）。
 *
 * 旧后端保存流程图不做任何校验（坏图到执行期才失败），此处按本批任务约定前移为
 * 保存/发布时拒绝；失败统一收敛为 [FlowGraphValidation.Invalid]，由服务层映射为
 * 旧契约错误结构（{code:400, message, error}）。
 */
@Component
class FlowGraphPayloadValidator {
    private val dslValidator = DslValidator()

    fun validate(flowGraph: String): FlowGraphValidation =
        when (val parsed = DslParser.parseFlowGraph(flowGraph.trim())) {
            is ParseResult.Success -> {
                val result = dslValidator.validate(parsed.value)
                val errors = result.errors
                if (errors.isEmpty()) {
                    FlowGraphValidation.Valid(parsed.value)
                } else {
                    FlowGraphValidation.Invalid(errors.joinToString("; ") { "${it.path}: ${it.message}" })
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
                FlowGraphValidation.Invalid("${parsed.error.reason}$location$path")
            }
        }

    /** 校验并返回图模型，失败时抛 IllegalArgumentException（消息带统一前缀） */
    fun validateOrThrow(flowGraph: String): FlowGraph =
        when (val result = validate(flowGraph)) {
            is FlowGraphValidation.Valid -> result.graph
            is FlowGraphValidation.Invalid -> throw IllegalArgumentException("流程图数据非法: ${result.detail}")
        }
}
