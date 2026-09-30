package com.example.ruleengine.engine

/**
 * 脚本引擎异常基类（对应旧 RuleExecutionException 的定位）。
 */
open class ScriptEngineException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * 脚本编译失败：语法错误，或静态审计 / 沙箱白名单拦截。
 */
class ScriptCompilationException(
    message: String,
    cause: Throwable? = null,
) : ScriptEngineException(message, cause)

/**
 * 脚本运行失败：执行期抛出异常。
 */
class ScriptExecutionException(
    message: String,
    cause: Throwable? = null,
) : ScriptEngineException(message, cause)

/**
 * 脚本执行超时：已向脚本线程发出中断（循环体含中断检查点，可在下一轮迭代退出）。
 */
class ScriptTimeoutException(
    message: String,
    cause: Throwable? = null,
) : ScriptEngineException(message, cause)
