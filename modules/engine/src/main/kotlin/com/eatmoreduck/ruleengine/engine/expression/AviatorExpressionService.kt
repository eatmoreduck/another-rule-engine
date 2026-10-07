package com.eatmoreduck.ruleengine.engine.expression

import com.googlecode.aviator.AviatorEvaluator
import com.googlecode.aviator.Expression
import com.googlecode.aviator.Options
import java.util.concurrent.ConcurrentHashMap

/** 表达式求值失败（编译错误 / 运行时错误），携带可读原因 */
class ExpressionEvaluationException(
    val expression: String,
    reason: String,
    cause: Throwable? = null,
) : RuntimeException("表达式求值失败: $reason（表达式: $expression）", cause)

/**
 * Aviator 表达式求值服务（衍生特征公式基础设施）。
 *
 * 设计要点：
 * - **编译缓存**：表达式字符串 → 编译后的 [Expression]（字节码），ConcurrentHashMap 去重，
 *   同一公式全生命周期只编译一次；Aviator 自身 compiled-cache 亦开启兜底。
 * - **金额精度**：[Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL] 开启后
 *   浮点字面量按 BigDecimal 运算，避免二进制浮点误差污染风控计分。
 * - **安全模型**：Aviator 默认禁止调用任意 Java 方法（函数只能来自内置库与显式注册），
 *   公式配置方无法触达文件/反射等能力——比脚本类引擎的沙箱负担小得多。
 * - **线程安全**：[AviatorEvaluator] 全局实例编译与求值均线程安全，可并发使用。
 */
class AviatorExpressionService {
    private val compiledCache = ConcurrentHashMap<String, Expression>()
    private val evaluator = AviatorEvaluator.getInstance()

    init {
        evaluator.setOption(Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, true)
        evaluator.setOption(Options.OPTIMIZE_LEVEL, AviatorEvaluator.COMPILE)
    }

    /**
     * 求值表达式。
     *
     * @param env 变量环境（特征值 Map）；表达式中引用了 env 缺失的变量时抛
     *   [ExpressionEvaluationException]（调用方决定降级语义，如视为特征缺失）
     * @throws ExpressionEvaluationException 编译或求值失败
     */
    fun evaluate(
        expression: String,
        env: Map<String, Any?>,
    ): Any? =
        try {
            compile(expression).execute(env)
        } catch (e: ExpressionEvaluationException) {
            throw e
        } catch (e: Exception) {
            throw ExpressionEvaluationException(expression, e.message ?: e.javaClass.simpleName, e)
        }

    /**
     * 提取表达式引用的全部变量名（依赖分析 / 防环校验用）。
     * @throws ExpressionEvaluationException 语法非法
     */
    fun variables(expression: String): List<String> =
        try {
            compile(expression).variableNames
        } catch (e: ExpressionEvaluationException) {
            throw e
        } catch (e: Exception) {
            throw ExpressionEvaluationException(expression, e.message ?: e.javaClass.simpleName, e)
        }

    /** 编译（缓存命中直接复用字节码）；语法非法抛 [ExpressionEvaluationException] */
    private fun compile(expression: String): Expression =
        try {
            compiledCache.computeIfAbsent(expression) { expr ->
                AviatorEvaluator.compile(expr, true)
            }
        } catch (e: Exception) {
            throw ExpressionEvaluationException(expression, e.message ?: e.javaClass.simpleName, e)
        }
}
