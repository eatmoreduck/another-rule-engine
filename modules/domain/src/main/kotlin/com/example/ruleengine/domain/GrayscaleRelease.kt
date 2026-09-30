package com.example.ruleengine.domain

import java.time.Instant

/**
 * 灰度发布聚合。
 *
 * 对应旧实体 GrayscaleConfig（grayscale_configs 表），提取业务概念、剔除持久化痕迹：
 * - 剔除 JPA 注解、数据库代理主键（保留可选 id 仅为应用层回传便利）
 * - 旧模型平铺的 strategyType + grayscalePercentage + featureRules + whitelistIds
 *   四个字段收敛为 [policy] 单一策略值对象（见 [GrayscalePolicy] 设计取舍）
 * - 旧 grayscalePercentage 记录列不再保留：灰度进度语义由策略参数与状态承载，
 *   COMPLETED 即全量，无需另存"100"
 *
 * 版本号语义：
 * - currentVersion：灰度开始前的现行版本
 * - grayscaleVersion：待验证的灰度版本，必须与现行版本不同（同版本灰度无意义）
 *
 * 不变式：
 * - currentVersion >= 1，grayscaleVersion >= 1，且两者不相等
 * - DRAFT：startedAt / completedAt 必须为空
 * - RUNNING / PAUSED：startedAt 必填、completedAt 必须为空
 * - COMPLETED：startedAt 与 completedAt 均必填（全量切换必经启动）
 * - ROLLED_BACK：completedAt 必填，startedAt 可空（允许"未启动即放弃"，旧模型 DRAFT -> ROLLED_BACK）
 * - createdBy 非空白
 *
 * 状态迁移经 [GrayscaleStatus.transition] 严格校验，非法迁移抛 [IllegalTransitionException]。
 */
data class GrayscaleRelease(
    val id: Long? = null,
    val target: GrayscaleTarget,
    val currentVersion: Int,
    val grayscaleVersion: Int,
    val policy: GrayscalePolicy,
    val status: GrayscaleStatus = GrayscaleStatus.DRAFT,
    val dualRunEnabled: Boolean = false,
    val description: String? = null,
    val createdBy: String,
    val createdAt: Instant,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
) {
    init {
        require(createdBy.isNotBlank()) { "createdBy 不能为空白" }
        require(currentVersion >= 1) { "currentVersion 必须 >= 1，实际: $currentVersion" }
        require(grayscaleVersion >= 1) { "grayscaleVersion 必须 >= 1，实际: $grayscaleVersion" }
        require(grayscaleVersion != currentVersion) {
            "灰度版本($grayscaleVersion)不得与现行版本($currentVersion)相同"
        }
        when (status) {
            GrayscaleStatus.DRAFT -> {
                require(startedAt == null) { "DRAFT 状态不允许携带 startedAt" }
                require(completedAt == null) { "DRAFT 状态不允许携带 completedAt" }
            }

            GrayscaleStatus.RUNNING, GrayscaleStatus.PAUSED -> {
                require(startedAt != null) { "RUNNING/PAUSED 状态必须携带 startedAt" }
                require(completedAt == null) { "RUNNING/PAUSED 状态不允许携带 completedAt" }
            }

            GrayscaleStatus.COMPLETED -> {
                // 全量切换必经启动，两个时间戳都必须存在
                require(startedAt != null) { "COMPLETED 状态必须携带 startedAt" }
                require(completedAt != null) { "COMPLETED 状态必须携带 completedAt" }
            }

            GrayscaleStatus.ROLLED_BACK -> {
                // 回滚允许"未启动即放弃"（旧模型 DRAFT -> ROLLED_BACK），startedAt 可为空
                require(completedAt != null) { "ROLLED_BACK 状态必须携带 completedAt" }
            }
        }
    }

    /**
     * 请求是否走灰度版本（纯函数）。
     *
     * 只有 RUNNING 状态参与分流：PAUSED 暂停后分流停止，与旧 resolveGrayscaleVersion
     * 仅查询 RUNNING 配置的语义一致。
     *
     * @param features 请求特征数据
     * @return true 表示该请求应使用灰度版本
     */
    fun isCanaryRequest(features: Map<String, Any?>): Boolean = status == GrayscaleStatus.RUNNING && policy.matches(features)

    /** 启动灰度（DRAFT/PAUSED -> RUNNING） */
    fun start(at: Instant): GrayscaleRelease = transitionTo(GrayscaleStatus.RUNNING, startedAt = at)

    /** 暂停灰度（RUNNING -> PAUSED），保留原 startedAt */
    fun pause(): GrayscaleRelease = transitionTo(GrayscaleStatus.PAUSED)

    /** 全量切换完成（RUNNING/PAUSED -> COMPLETED），[at] 记录为完成时间 */
    fun complete(at: Instant): GrayscaleRelease = transitionTo(GrayscaleStatus.COMPLETED, completedAt = at)

    /** 回滚（DRAFT/RUNNING/PAUSED -> ROLLED_BACK，终态），[at] 记录为结束时间 */
    fun rollback(at: Instant): GrayscaleRelease = transitionTo(GrayscaleStatus.ROLLED_BACK, completedAt = at)

    private fun transitionTo(
        target: GrayscaleStatus,
        startedAt: Instant? = this.startedAt,
        completedAt: Instant? = this.completedAt,
    ): GrayscaleRelease {
        GrayscaleStatus.transition(status, target)
        return copy(status = target, startedAt = startedAt, completedAt = completedAt)
    }
}
