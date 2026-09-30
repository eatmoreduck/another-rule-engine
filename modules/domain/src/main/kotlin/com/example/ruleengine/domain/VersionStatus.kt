package com.example.ruleengine.domain

/**
 * 规则版本状态。
 *
 * 从旧常量 VersionStatus 原样提取，合法迁移以旧 VersionManagementService 的守卫为准：
 * - DRAFT -> CANARY：进入灰度验证
 * - DRAFT -> ACTIVE：直接发布
 * - CANARY -> ACTIVE：灰度验证通过后发布（旧守卫"只有 DRAFT 或 CANARY 状态的版本才能发布"）
 * - ACTIVE -> ARCHIVED：被新版本替代（旧 publishVersion 将原 ACTIVE 版本置为 ARCHIVED）
 * - ARCHIVED / DRAFT 不可逆，ARCHIVED 为终态
 */
enum class VersionStatus {
    DRAFT,
    CANARY,
    ACTIVE,
    ARCHIVED,
    ;

    /** 当前状态允许迁移到的目标状态集合（合法迁移表） */
    val legalTargets: Set<VersionStatus>
        get() =
            when (this) {
                DRAFT -> setOf(CANARY, ACTIVE)
                CANARY -> setOf(ACTIVE)
                ACTIVE -> setOf(ARCHIVED)
                ARCHIVED -> emptySet()
            }

    /** 判断从当前状态迁移到 [target] 是否合法 */
    fun canTransitionTo(target: VersionStatus): Boolean = target in legalTargets

    /** 是否允许发布（对应旧守卫：只有 DRAFT 或 CANARY 状态的版本才能发布） */
    val publishable: Boolean
        get() = this == DRAFT || this == CANARY

    companion object {
        /**
         * 显式状态迁移：非法迁移抛出 [IllegalTransitionException]，不静默。
         *
         * @param from 迁移前状态
         * @param to 迁移目标状态
         */
        fun transition(
            from: VersionStatus,
            to: VersionStatus,
        ): VersionStatus {
            if (!from.canTransitionTo(to)) {
                throw IllegalTransitionException(from.name, to.name, "版本生命周期")
            }
            return to
        }
    }
}
