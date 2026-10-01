package com.eatmoreduck.ruleengine.engine

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.VariableScope
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ArrayExpression
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.BitwiseNegationExpression
import org.codehaus.groovy.ast.expr.BooleanExpression
import org.codehaus.groovy.ast.expr.CastExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.NotExpression
import org.codehaus.groovy.ast.expr.PostfixExpression
import org.codehaus.groovy.ast.expr.PrefixExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression
import org.codehaus.groovy.ast.expr.TernaryExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.UnaryMinusExpression
import org.codehaus.groovy.ast.expr.UnaryPlusExpression
import org.codehaus.groovy.ast.stmt.AssertStatement
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.CatchStatement
import org.codehaus.groovy.ast.stmt.DoWhileStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ForStatement
import org.codehaus.groovy.ast.stmt.IfStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.stmt.SwitchStatement
import org.codehaus.groovy.ast.stmt.TryCatchStatement
import org.codehaus.groovy.ast.stmt.WhileStatement
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import java.util.Collections
import java.util.IdentityHashMap

/**
 * 中断检查点宿主辅助类：脚本字节码在每轮循环迭代开头调用 [check]。
 *
 * 为什么不能在脚本里直接写 Thread.currentThread().isInterrupted()？
 * 因为 Thread 在接收者黑名单中（正确行为）；因此改由宿主类代为检查——
 * 被中断时抛出 InterruptedException，使超时取消（Future.cancel(true)）
 * 能真正终止死循环脚本，而不是留下一个永远烧 CPU 的孤儿线程。
 */
object ScriptInterrupts {
    /** 循环体每轮迭代调用：宿主线程被中断时抛出 InterruptedException 终止脚本 */
    @JvmStatic
    fun check() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("脚本执行已被中断（超时或手动取消）")
        }
    }
}

/**
 * 循环中断注入定制器：在转换期（CONVERSION）改写 AST，
 * 为 while / for / do-while 的循环体以及所有闭包体开头插入
 * `ScriptInterrupts.check()` 静态调用。
 *
 * 该调用是宿主类静态方法、不引用任何局部变量，且 ScriptInterrupts
 * 不在接收者黑名单中，因此注入的节点能通过 SecureASTCustomizer 的后续校验。
 */
class LoopInterruptGuard : CompilationCustomizer(CompilePhase.CONVERSION) {
    override fun call(
        source: SourceUnit,
        context: GeneratorContext,
        classNode: ClassNode,
    ) {
        // 按对象身份去重：闭包可能同时出现在语句树与内部类方法两条路径上，避免重复注入
        val guardedStatements: MutableSet<Statement> = Collections.newSetFromMap(IdentityHashMap())
        classNode.methods.forEach { method -> rewriteStatement(method.code, guardedStatements) }
        classNode.declaredConstructors.forEach { constructor -> rewriteStatement(constructor.code, guardedStatements) }
    }

    /** 递归遍历语句树：对循环体注入中断检查，同时深入闭包体（闭包同样可能包含死循环） */
    private fun rewriteStatement(
        statement: Statement,
        guardedStatements: MutableSet<Statement>,
    ) {
        if (!guardedStatements.add(statement)) return
        when (statement) {
            is BlockStatement -> {
                statement.statements.forEach { rewriteStatement(it, guardedStatements) }
            }

            is WhileStatement -> {
                rewriteStatement(statement.loopBlock, guardedStatements)
                statement.loopBlock = injectGuard(statement.loopBlock)
            }

            is DoWhileStatement -> {
                rewriteStatement(statement.loopBlock, guardedStatements)
                statement.loopBlock = injectGuard(statement.loopBlock)
            }

            is ForStatement -> {
                rewriteExpression(statement.collectionExpression, guardedStatements)
                rewriteStatement(statement.loopBlock, guardedStatements)
                statement.loopBlock = injectGuard(statement.loopBlock)
            }

            is IfStatement -> {
                rewriteStatement(statement.ifBlock, guardedStatements)
                rewriteStatement(statement.elseBlock, guardedStatements)
            }

            is TryCatchStatement -> {
                rewriteStatement(statement.tryStatement, guardedStatements)
                statement.catchStatements.forEach { catchStatement: CatchStatement ->
                    rewriteStatement(catchStatement.code, guardedStatements)
                }
                rewriteStatement(statement.finallyStatement, guardedStatements)
            }

            is SwitchStatement -> {
                statement.caseStatements.forEach { caseStatement ->
                    rewriteStatement(caseStatement.code, guardedStatements)
                }
                rewriteStatement(statement.defaultStatement, guardedStatements)
            }

            is ExpressionStatement -> {
                rewriteExpression(statement.expression, guardedStatements)
            }

            is ReturnStatement -> {
                rewriteExpression(statement.expression, guardedStatements)
            }

            is AssertStatement -> {
                rewriteExpression(statement.booleanExpression, guardedStatements)
                rewriteExpression(statement.messageExpression, guardedStatements)
            }

            else -> {
                Unit
            }
        }
    }

    /** 递归遍历表达式树，只为发现闭包（闭包体是需要注入中断检查的语句块） */
    private fun rewriteExpression(
        expression: Expression,
        guardedStatements: MutableSet<Statement>,
    ) {
        when (expression) {
            is ClosureExpression -> {
                rewriteStatement(expression.code, guardedStatements)
            }

            is MethodCallExpression -> {
                rewriteExpression(expression.objectExpression, guardedStatements)
                rewriteExpression(expression.arguments, guardedStatements)
            }

            is ConstructorCallExpression -> {
                rewriteExpression(expression.arguments, guardedStatements)
            }

            is StaticMethodCallExpression -> {
                rewriteExpression(expression.arguments, guardedStatements)
            }

            is TupleExpression -> {
                expression.expressions.forEach { rewriteExpression(it, guardedStatements) }
            }

            is DeclarationExpression -> {
                rewriteExpression(expression.leftExpression, guardedStatements)
                rewriteExpression(expression.rightExpression, guardedStatements)
            }

            is BinaryExpression -> {
                rewriteExpression(expression.leftExpression, guardedStatements)
                rewriteExpression(expression.rightExpression, guardedStatements)
            }

            is TernaryExpression -> {
                rewriteExpression(expression.booleanExpression, guardedStatements)
                rewriteExpression(expression.trueExpression, guardedStatements)
                rewriteExpression(expression.falseExpression, guardedStatements)
            }

            is BooleanExpression -> {
                rewriteExpression(expression.expression, guardedStatements)
            }

            is NotExpression -> {
                rewriteExpression(expression.expression, guardedStatements)
            }

            is UnaryMinusExpression -> {
                rewriteExpression(expression.expression, guardedStatements)
            }

            is UnaryPlusExpression -> {
                rewriteExpression(expression.expression, guardedStatements)
            }

            is BitwiseNegationExpression -> {
                rewriteExpression(expression.expression, guardedStatements)
            }

            is CastExpression -> {
                rewriteExpression(expression.expression, guardedStatements)
            }

            is PropertyExpression -> {
                rewriteExpression(expression.objectExpression, guardedStatements)
            }

            is GStringExpression -> {
                expression.values.forEach { rewriteExpression(it, guardedStatements) }
            }

            is ListExpression -> {
                expression.expressions.forEach { rewriteExpression(it, guardedStatements) }
            }

            is MapExpression -> {
                expression.mapEntryExpressions.forEach { entry: MapEntryExpression ->
                    rewriteExpression(entry.keyExpression, guardedStatements)
                    rewriteExpression(entry.valueExpression, guardedStatements)
                }
            }

            is ArrayExpression -> {
                expression.expressions.forEach { rewriteExpression(it, guardedStatements) }
            }

            is PostfixExpression -> {
                rewriteExpression(expression.expression, guardedStatements)
            }

            is PrefixExpression -> {
                rewriteExpression(expression.expression, guardedStatements)
            }

            else -> {
                Unit
            }
        }
    }

    /**
     * 返回注入了中断检查的循环体：
     * 块语句体直接在开头插入检查语句（不引入新作用域，避免影响变量解析）；
     * 非块语句体包一层 BlockStatement（检查为纯静态调用，不引入局部变量）。
     */
    private fun injectGuard(originalLoopBody: Statement): Statement {
        val guard = ExpressionStatement(buildInterruptCheck())
        return if (originalLoopBody is BlockStatement) {
            originalLoopBody.statements.add(0, guard)
            originalLoopBody
        } else {
            BlockStatement(listOf(guard, originalLoopBody), VariableScope())
        }
    }

    /** 构造 `ScriptInterrupts.check()` 的静态调用表达式 */
    private fun buildInterruptCheck(): Expression =
        MethodCallExpression(
            ClassExpression(ClassHelper.make(ScriptInterrupts::class.java)),
            "check",
            ArgumentListExpression.EMPTY_ARGUMENTS,
        )
}
