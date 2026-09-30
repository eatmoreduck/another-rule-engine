package com.example.ruleengine.domain

/**
 * 灰度发布状态。
 *
 * 从旧常量 GrayscaleStatus 原样提取，合法迁移以旧 GrayscaleService 的守卫为准：
 * - DRAFT -> RUNNING：启动灰度（startGrayscale 允许 DRAFT 或 PAUSED 启动）
 * - DRAFT -> ROLLED_BACK：未启动即放弃（rollbackGrayscale 只排除 COMPLETED 与 ROLLED_BACK）
 * - RUNNING -> PAUSED：暂停
 * - PAUSED -> RUNNING：恢复启动
 * - RUNNING/PAUSED -> COMPLETED：全量切换（completeGrayscale 允许 RUNNING 或 PAUSED 完成）
 * - RUNNING/PAUSED -> ROLLED_BACK：回滚
 * - COMPLETED / ROLLED_BACK 为终态，不可再迁移
 */
enum class GrayscaleStatus {
    DRAFT,
    RUNNING,
    PAUSED,
    COMPLETED,
    ROLLED_BACK,
    ;

    /** 当前状态允许迁移到的目标状态集合（合法迁移表） */
    val legalTargets: Set<GrayscaleStatus>
        get() =
            when (this) {
                DRAFT -> setOf(RUNNING, ROLLED_BACK)
                RUNNING -> setOf(PAUSED, COMPLETED, ROLLED_BACK)
                PAUSED -> setOf(RUNNING, COMPLETED, ROLLED_BACK)
                COMPLETED -> emptySet()
                ROLLED_BACK -> emptySet()
            }

    /** 判断从当前状态迁移到 [target] 是否合法 */
    fun canTransitionTo(target: GrayscaleStatus): Boolean = target in legalTargets

    companion object {
        /**
         * 显式状态迁移：非法迁移抛出 [IllegalTransitionException]，不静默。
         *
         * @param from 迁移前状态
         * @param to 迁移目标状态
         */
        fun transition(
            from: GrayscaleStatus,
            to: GrayscaleStatus,
        ): GrayscaleStatus {
            if (!from.canTransitionTo(to)) {
                throw IllegalTransitionException(from.name, to.name, "灰度发布")
            }
            return to
        }
    }
}
