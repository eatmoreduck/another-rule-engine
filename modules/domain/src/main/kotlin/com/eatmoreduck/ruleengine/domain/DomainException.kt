package com.eatmoreduck.ruleengine.domain

/**
 * 领域规则违反异常基类。
 *
 * 领域层对业务约束被破坏时的统一表达，应用层与 Web 层可据此映射为 4xx 语义，
 * 与框架层的 IllegalStateException 解耦，便于区分"领域约束"与"程序缺陷"。
 */
open class DomainException(
    message: String,
) : RuntimeException(message)

/**
 * 非法状态迁移异常。
 *
 * 状态机迁移守卫失败时抛出，禁止静默忽略或降级处理。
 *
 * @param from 迁移前状态
 * @param to 迁移目标状态
 * @param context 迁移发生所在的业务上下文（如"规则生命周期"、"灰度发布"）
 */
class IllegalTransitionException(
    val from: String,
    val to: String,
    context: String,
) : DomainException("不允许的状态迁移 [$context]: $from -> $to")
