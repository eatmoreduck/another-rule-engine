package com.eatmoreduck.ruleengine.storage.repository

import java.time.Instant

/**
 * 决策流主表行（列与 V9 + V14 一致）。
 * 状态语义（DRAFT/ACTIVE/ARCHIVED/DELETED）与版本指针迁移的合法性校验在服务层，
 * 本层只负责持久化。
 */
data class DecisionFlowMain(
    val id: Long?,
    val flowKey: String,
    val flowName: String,
    val flowDescription: String?,
    val flowGraph: String,
    /** 最新内容版本号（decision_flows.version） */
    val version: Int,
    /** 当前生效版本号，NULL 视为未显式发布 */
    val activeVersion: Int?,
    val status: String,
    val createdBy: String,
    val createdAt: Instant,
    val updatedBy: String?,
    val updatedAt: Instant?,
    val enabled: Boolean,
    val environmentId: Long?,
)

/** 决策流版本行 */
data class DecisionFlowVersion(
    val id: Long?,
    val flowId: Long,
    val flowKey: String,
    val version: Int,
    val flowGraph: String,
    val changeReason: String?,
    val changedBy: String,
    val changedAt: Instant,
    val isRollback: Boolean,
    val rollbackFromVersion: Int?,
    /** DRAFT/CANARY/ACTIVE/ARCHIVED，历史行 NULL 兜底为 ACTIVE（与 V14 迁移默认一致） */
    val status: String,
)

/**
 * 决策流仓储：decision-api（按 key 加载流程图执行）与 admin-api（流程管理 CRUD）共用。
 * 事务边界由调用方（Spring @Transactional 或测试事务）提供，本接口不开事务。
 */
interface DecisionFlowRepository {
    // ---------- 读 ----------

    fun findMain(flowKey: String): DecisionFlowMain?

    fun findAllMains(): List<DecisionFlowMain>

    fun findVersion(
        flowKey: String,
        version: Int,
    ): DecisionFlowVersion?

    /** 全部版本，version 降序 */
    fun findVersionsByFlowKey(flowKey: String): List<DecisionFlowVersion>

    // ---------- 写（管理侧 CRUD，状态迁移校验在服务层） ----------

    /** 插入主表行并回读（含生成 id） */
    fun insertMain(main: DecisionFlowMain): DecisionFlowMain

    /** 插入版本行并回读（含生成 id） */
    fun insertVersion(version: DecisionFlowVersion): DecisionFlowVersion

    /** 更新主表元信息（名称/描述/环境），并落 updated_by/updated_at */
    fun updateMainMeta(
        flowKey: String,
        flowName: String,
        flowDescription: String?,
        environmentId: Long?,
        updatedBy: String,
    ): Int

    /**
     * 推进主表版本指针（发布/全量切换/回滚共用）：version、activeVersion、flowGraph 一起更新。
     * [activeVersion] 传 null 表示清除生效指针（仅 DRAFT 场景）。
     */
    fun updateMainPointer(
        flowKey: String,
        version: Int,
        activeVersion: Int?,
        flowGraph: String,
        updatedBy: String,
    ): Int

    fun updateMainStatus(
        flowKey: String,
        status: String,
        updatedBy: String,
    ): Int

    fun setMainEnabled(
        flowKey: String,
        enabled: Boolean,
        updatedBy: String,
    ): Int

    /** 更新指定版本的状态（DRAFT→CANARY→ACTIVE→ARCHIVED 的持久化侧） */
    fun updateVersionStatus(
        flowKey: String,
        version: Int,
        status: String,
    ): Int
}
