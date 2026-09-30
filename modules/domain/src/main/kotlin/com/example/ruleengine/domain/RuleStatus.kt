package com.example.ruleengine.domain

/**
 * 规则生命周期状态。
 *
 * 从旧模型的 Rule.enabled + Rule.deleted 两列组合提取（规则主表没有独立状态列，
 * "草稿/生效"的语义由 RuleVersion 的版本状态承载，本状态只表达规则本身的启停与删除）：
 * - ENABLED：启用中（旧 enabled=true, deleted=false）
 * - DISABLED：已停用（旧 enabled=false, deleted=false）
 * - DELETED：已软删除（旧 deleted=true，终态，不可恢复）
 */
enum class RuleStatus {
    ENABLED,
    DISABLED,
    DELETED,
    ;

    /** 当前状态允许迁移到的目标状态集合（合法迁移表） */
    val legalTargets: Set<RuleStatus>
        get() =
            when (this) {
                ENABLED -> setOf(DISABLED, DELETED)
                DISABLED -> setOf(ENABLED, DELETED)
                DELETED -> emptySet()
            }

    /** 判断从当前状态迁移到 [target] 是否合法 */
    fun canTransitionTo(target: RuleStatus): Boolean = target in legalTargets

    companion object {
        /**
         * 显式状态迁移：非法迁移抛出 [IllegalTransitionException]，不静默。
         *
         * @param from 迁移前状态
         * @param to 迁移目标状态
         */
        fun transition(
            from: RuleStatus,
            to: RuleStatus,
        ): RuleStatus {
            if (!from.canTransitionTo(to)) {
                throw IllegalTransitionException(from.name, to.name, "规则生命周期")
            }
            return to
        }
    }
}
