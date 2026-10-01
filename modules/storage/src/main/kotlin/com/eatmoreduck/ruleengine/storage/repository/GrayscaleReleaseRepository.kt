package com.eatmoreduck.ruleengine.storage.repository

import com.eatmoreduck.ruleengine.domain.GrayscaleRelease
import com.eatmoreduck.ruleengine.domain.GrayscaleStatus
import com.eatmoreduck.ruleengine.domain.GrayscaleTarget

/**
 * 灰度发布仓储（对应旧 GrayscaleConfigRepository 的派生查询职责）。
 */
interface GrayscaleReleaseRepository {
    /**
     * 新增或更新灰度配置行。
     * - [GrayscaleRelease.id] 为 null → 插入，返回携带生成 id 的副本
     * - 非 null → 按 id 全列更新，目标行不存在时抛 [com.eatmoreduck.ruleengine.storage.StorageConflictException]
     */
    fun save(release: GrayscaleRelease): GrayscaleRelease

    fun findById(id: Long): GrayscaleRelease?

    /** 目标当前处于 RUNNING 的灰度配置（决策分流热路径；同目标同时至多一条，取最新创建） */
    fun findRunningByTarget(target: GrayscaleTarget): GrayscaleRelease?

    /** 目标的全部灰度历史，按创建时间降序 */
    fun findByTarget(target: GrayscaleTarget): List<GrayscaleRelease>

    /** 指定状态的全部灰度配置，按创建时间降序 */
    fun findByStatus(status: GrayscaleStatus): List<GrayscaleRelease>

    /** 全部灰度配置，按创建时间降序（管理端列表） */
    fun findAll(): List<GrayscaleRelease>
}
