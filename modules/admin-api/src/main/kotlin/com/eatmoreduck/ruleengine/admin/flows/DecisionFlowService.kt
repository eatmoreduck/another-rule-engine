package com.eatmoreduck.ruleengine.admin.flows

import com.eatmoreduck.ruleengine.admin.cache.publishInvalidation
import com.eatmoreduck.ruleengine.admin.dto.CreateDecisionFlowRequest
import com.eatmoreduck.ruleengine.admin.dto.CreateFlowVersionRequest
import com.eatmoreduck.ruleengine.admin.dto.DecisionFlowQuery
import com.eatmoreduck.ruleengine.admin.dto.DecisionFlowResponse
import com.eatmoreduck.ruleengine.admin.dto.DecisionFlowVersionResponse
import com.eatmoreduck.ruleengine.admin.dto.FlowRollbackRequest
import com.eatmoreduck.ruleengine.admin.dto.PageResponse
import com.eatmoreduck.ruleengine.admin.dto.UpdateDecisionFlowRequest
import com.eatmoreduck.ruleengine.shared.cache.CacheInvalidationType
import com.eatmoreduck.ruleengine.storage.EntityNotFoundException
import com.eatmoreduck.ruleengine.storage.repository.DecisionFlowMain
import com.eatmoreduck.ruleengine.storage.repository.DecisionFlowRepository
import com.eatmoreduck.ruleengine.storage.repository.DecisionFlowVersion
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId

/**
 * 决策流生命周期与版本管理服务。
 *
 * 业务规则照搬旧 DecisionFlowLifecycleService / DecisionFlowVersionManagementService
 * （守卫消息逐字一致），行为经 2b 规则版本同款语义修正：
 * - 保存与发布前用 DslParser + DslValidator 校验流程图（旧实现不校验，坏图到执行期才失败）；
 * - 更新推进版本时归档旧 ACTIVE 版本并推进 activeVersion 指针（旧实现新版本行写 ACTIVE
 *   但主表 activeVersion 不动，与灰度全量切换 switchFlowVersion 的推进语义不一致）；
 * - 新建草稿版本落 DRAFT（不改变生效版本），发布走状态迁移守卫
 *   （只有 DRAFT/CANARY 可发布，消息与旧 publishVersion 逐字一致）；
 * - 回滚以更高版本号落地目标内容副本并立即生效（旧实现不归档不推进生效指针）。
 *
 * 与灰度的兼容：发布/回滚的指针推进（version + activeVersion + flowGraph 三件套）
 * 与 GrayscaleService.completeGrayscale 的 switchFlowVersion 完全同构，
 * 互不重复实现、互不冲突（CANARY 落库仍由灰度服务负责）。
 */
@Service
class DecisionFlowService(
    private val flowRepository: DecisionFlowRepository,
    private val graphValidator: FlowGraphPayloadValidator,
    private val eventPublisher: ApplicationEventPublisher,
) {
    // ---------- 生命周期 ----------

    /** 创建决策流：版本号从 1 起，落初始版本行（changeReason 固定"创建决策流"，照旧） */
    @Transactional
    fun createFlow(
        request: CreateDecisionFlowRequest,
        operator: String,
    ): DecisionFlowResponse {
        if (flowRepository.existsActiveMain(request.flowKey)) {
            throw IllegalArgumentException("决策流Key已存在: ${request.flowKey}")
        }
        graphValidator.validateOrThrow(request.flowGraph)

        val now = Instant.now()
        val saved =
            flowRepository.insertMain(
                DecisionFlowMain(
                    id = null,
                    flowKey = request.flowKey,
                    flowName = request.flowName,
                    flowDescription = request.flowDescription,
                    flowGraph = request.flowGraph,
                    version = 1,
                    activeVersion = null,
                    status = FLOW_STATUS_DRAFT,
                    createdBy = operator,
                    createdAt = now,
                    updatedBy = null,
                    updatedAt = null,
                    enabled = true,
                    environmentId = null,
                ),
            )
        // 初始版本行状态 ACTIVE（照旧实现），activeVersion 指针由发布/保存路径推进
        flowRepository.insertVersion(
            DecisionFlowVersion(
                id = null,
                flowId = saved.id!!,
                flowKey = saved.flowKey,
                version = 1,
                flowGraph = saved.flowGraph,
                changeReason = "创建决策流",
                changedBy = operator,
                changedAt = now,
                isRollback = false,
                rollbackFromVersion = null,
                status = VERSION_STATUS_ACTIVE,
            ),
        )
        log.info("创建决策流: flowKey={}, operator={}", request.flowKey, operator)
        eventPublisher.publishInvalidation(CacheInvalidationType.FLOW, request.flowKey)
        return toResponse(saved)
    }

    /**
     * 更新决策流：元数据逐字段合并（null 不覆盖，照旧）；
     * flowGraph 变化时推进版本（解析+结构校验 → 归档旧 ACTIVE → 落新 ACTIVE 版本 → 主表指针推进）。
     */
    @Transactional
    fun updateFlow(
        flowKey: String,
        request: UpdateDecisionFlowRequest,
        operator: String,
    ): DecisionFlowResponse {
        val main =
            flowRepository.findMain(flowKey)
                ?: throw EntityNotFoundException("DecisionFlow", flowKey)

        val newFlowName = request.flowName ?: main.flowName
        val newFlowDescription = request.flowDescription ?: main.flowDescription
        flowRepository.updateMainMeta(flowKey, newFlowName, newFlowDescription, main.environmentId, operator)

        if (request.flowGraph != null && request.flowGraph != main.flowGraph) {
            graphValidator.validateOrThrow(request.flowGraph)
            val newVersion = main.version + 1
            ensureVersionSlotFree(flowKey, newVersion)

            archiveActiveVersions(flowKey)
            flowRepository.insertVersion(
                DecisionFlowVersion(
                    id = null,
                    flowId = main.id!!,
                    flowKey = flowKey,
                    version = newVersion,
                    flowGraph = request.flowGraph,
                    changeReason = request.changeReason,
                    changedBy = operator,
                    changedAt = Instant.now(),
                    isRollback = false,
                    rollbackFromVersion = null,
                    status = VERSION_STATUS_ACTIVE,
                ),
            )
            // 与灰度 switchFlowVersion 同构的指针推进：版本号、生效指针、流程图一起走
            flowRepository.updateMainPointer(flowKey, newVersion, newVersion, request.flowGraph, operator)
            log.info("更新决策流图: flowKey={}, version={}, operator={}", flowKey, newVersion, operator)
        } else {
            log.info("更新决策流元数据: flowKey={}, operator={}", flowKey, operator)
        }
        // 图变更（版本指针推进）与元数据变更统一广播：决策侧流主行/流图缓存整键失效
        eventPublisher.publishInvalidation(CacheInvalidationType.FLOW, flowKey)
        return toResponse(flowRepository.findMain(flowKey) ?: main)
    }

    /** 删除决策流（软删除：status=DELETED + enabled=false，照旧） */
    @Transactional
    fun deleteFlow(
        flowKey: String,
        operator: String,
    ) {
        val main =
            flowRepository.findMain(flowKey)
                ?: throw EntityNotFoundException("DecisionFlow", flowKey)
        flowRepository.updateMainStatus(flowKey, FLOW_STATUS_DELETED, operator)
        flowRepository.setMainEnabled(flowKey, false, operator)
        log.info("删除决策流: flowKey={}, operator={}", flowKey, main.flowKey)
        eventPublisher.publishInvalidation(CacheInvalidationType.FLOW, flowKey)
    }

    /** 启用决策流（status=ACTIVE + enabled=true，照旧） */
    @Transactional
    fun enableFlow(
        flowKey: String,
        operator: String,
    ): DecisionFlowResponse {
        flowRepository.findMain(flowKey) ?: throw EntityNotFoundException("DecisionFlow", flowKey)
        flowRepository.updateMainStatus(flowKey, FLOW_STATUS_ACTIVE, operator)
        flowRepository.setMainEnabled(flowKey, true, operator)
        eventPublisher.publishInvalidation(CacheInvalidationType.FLOW, flowKey)
        return toResponse(flowRepository.findMain(flowKey)!!)
    }

    /** 禁用决策流（仅动 enabled，照旧） */
    @Transactional
    fun disableFlow(
        flowKey: String,
        operator: String,
    ): DecisionFlowResponse {
        flowRepository.findMain(flowKey) ?: throw EntityNotFoundException("DecisionFlow", flowKey)
        flowRepository.setMainEnabled(flowKey, false, operator)
        eventPublisher.publishInvalidation(CacheInvalidationType.FLOW, flowKey)
        return toResponse(flowRepository.findMain(flowKey)!!)
    }

    /** 决策流详情 */
    @Transactional(readOnly = true)
    fun getFlow(flowKey: String): DecisionFlowResponse =
        flowRepository
            .findMain(flowKey)
            ?.let(::toResponse)
            ?: throw EntityNotFoundException("DecisionFlow", flowKey)

    /** 决策流列表（分页）；已删除（DELETED）的流不在默认列表展示，可用 query 接口按 status 显式查询 */
    @Transactional(readOnly = true)
    fun listFlows(
        page: Int,
        size: Int,
    ): PageResponse<DecisionFlowResponse> {
        val flows =
            flowRepository
                .findAllMains()
                .sortedBy { it.id }
                .filter { it.status != "DELETED" }
                .map(::toResponse)
        return PageResponse.of(flows, page, size)
    }

    /** 多条件查询（过滤语义照搬旧 findByConditionsWithTeam 的 JPQL：keyword 对 flowKey/flowName 双列模糊） */
    @Transactional(readOnly = true)
    fun queryFlows(
        query: DecisionFlowQuery,
        page: Int,
        size: Int,
    ): PageResponse<DecisionFlowResponse> {
        val zone = ZoneId.systemDefault()
        val createdAtStart = query.createdAtStart?.atZone(zone)?.toInstant()
        val createdAtEnd = query.createdAtEnd?.atZone(zone)?.toInstant()
        val updatedAtStart = query.updatedAtStart?.atZone(zone)?.toInstant()
        val updatedAtEnd = query.updatedAtEnd?.atZone(zone)?.toInstant()
        val statusFilter = query.status?.trim()?.takeIf { it.isNotEmpty() }
        val keyword = query.keyword?.trim()?.takeIf { it.isNotEmpty() }
        val flows =
            flowRepository
                .findAllMains()
                .sortedBy { it.id }
                .asSequence()
                .filter { statusFilter == null || it.status == statusFilter }
                .filter { query.createdBy == null || it.createdBy == query.createdBy }
                .filter { query.enabled == null || it.enabled == query.enabled }
                .filter {
                    keyword == null || it.flowKey.contains(keyword, ignoreCase = true) ||
                        it.flowName.contains(keyword, ignoreCase = true)
                }.filter { createdAtStart == null || !it.createdAt.isBefore(createdAtStart) }
                .filter { createdAtEnd == null || !it.createdAt.isAfter(createdAtEnd) }
                .filter { main ->
                    val updatedAt = main.updatedAt
                    updatedAtStart == null || (updatedAt != null && !updatedAt.isBefore(updatedAtStart))
                }.filter { main ->
                    val updatedAt = main.updatedAt
                    updatedAtEnd == null || (updatedAt != null && !updatedAt.isAfter(updatedAtEnd))
                }.toList()
        return PageResponse.of(flows.map(::toResponse), page, size)
    }

    // ---------- 版本管理 ----------

    /** 版本列表（版本号降序；不校验流程存在性，未知流程返回空列表，与 2b 规则版本一致） */
    @Transactional(readOnly = true)
    fun getVersions(flowKey: String): List<DecisionFlowVersionResponse> =
        flowRepository.findVersionsByFlowKey(flowKey).map(::toVersionResponse)

    /** 特定版本详情；不存在返回 null（控制器映射为旧契约的 404 空体） */
    @Transactional(readOnly = true)
    fun getVersion(
        flowKey: String,
        version: Int,
    ): DecisionFlowVersionResponse? = flowRepository.findVersion(flowKey, version)?.let(::toVersionResponse)

    /**
     * 新建草稿版本（DRAFT）：推进最新内容版本号，不改变生效版本与流程图。
     * 旧契约未暴露该端点（旧 createDraft 为主表 version 不推进的半成品），
     * 此处按 2b 规则版本语义补齐：主表 version 一并推进，防 TOCTOU 冲突检查。
     */
    @Transactional
    fun createDraftVersion(
        flowKey: String,
        request: CreateFlowVersionRequest,
        operator: String,
    ): DecisionFlowVersionResponse {
        val main =
            flowRepository.findMain(flowKey)
                ?: throw EntityNotFoundException("DecisionFlow", flowKey)
        graphValidator.validateOrThrow(request.flowGraph)

        val newVersion = main.version + 1
        ensureVersionSlotFree(flowKey, newVersion)

        val draft =
            flowRepository.insertVersion(
                DecisionFlowVersion(
                    id = null,
                    flowId = main.id!!,
                    flowKey = flowKey,
                    version = newVersion,
                    flowGraph = request.flowGraph,
                    changeReason = request.changeReason,
                    changedBy = operator,
                    changedAt = Instant.now(),
                    isRollback = false,
                    rollbackFromVersion = null,
                    status = VERSION_STATUS_DRAFT,
                ),
            )
        // 推进主表内容版本号，生效指针与流程图保持不动（DRAFT 不生效）
        flowRepository.updateMainPointer(flowKey, newVersion, main.activeVersion, main.flowGraph, operator)
        log.info("创建决策流草稿版本: flowKey={}, version={}, operator={}", flowKey, newVersion, operator)
        // DRAFT 不影响决策缓存，仍随流键广播（与其他变更路径保持一致口径）
        eventPublisher.publishInvalidation(CacheInvalidationType.FLOW, flowKey)
        return toVersionResponse(draft)
    }

    /**
     * 发布决策流版本：只有 DRAFT/CANARY 可发布（守卫消息与旧 publishVersion 逐字一致），
     * 旧 ACTIVE 版本归档、目标版本置 ACTIVE、主表指针推进（与灰度全量切换同构）。
     */
    @Transactional
    fun publishVersion(
        flowKey: String,
        version: Int,
        operator: String,
    ): DecisionFlowVersionResponse {
        flowRepository.findMain(flowKey) ?: throw EntityNotFoundException("DecisionFlow", flowKey)
        val target =
            flowRepository.findVersion(flowKey, version)
                ?: throw IllegalArgumentException("版本不存在: $version")

        if (target.status != VERSION_STATUS_DRAFT && target.status != VERSION_STATUS_CANARY) {
            throw IllegalArgumentException("只有草稿或灰度中的版本才能发布，当前状态: ${target.status}")
        }
        // 发布前校验目标图（保存入口之外的兜底：历史 DRAFT 数据可能未经校验）
        graphValidator.validateOrThrow(target.flowGraph)

        archiveActiveVersions(flowKey)
        flowRepository.updateVersionStatus(flowKey, version, VERSION_STATUS_ACTIVE)
        flowRepository.updateMainPointer(flowKey, version, version, target.flowGraph, operator)
        log.info("发布决策流版本: flowKey={}, version={}, operator={}", flowKey, version, operator)
        // 生效指针推进：决策侧流图载荷缓存需立即失效
        eventPublisher.publishInvalidation(CacheInvalidationType.FLOW, flowKey)
        return toVersionResponse(flowRepository.findVersion(flowKey, version) ?: target)
    }

    /**
     * 回滚决策流到指定版本：以更高版本号落地目标内容副本并立即发布生效
     * （2b 规则回滚同款语义；rollbackFromVersion 记录回滚依据的目标版本号）。
     */
    @Transactional
    fun rollback(
        flowKey: String,
        request: FlowRollbackRequest,
        operator: String,
    ): DecisionFlowVersionResponse {
        val targetVersion =
            request.targetVersion
                ?: throw IllegalArgumentException("目标版本号不能为空")
        val main =
            flowRepository.findMain(flowKey)
                ?: throw EntityNotFoundException("DecisionFlow", flowKey)
        val target =
            flowRepository.findVersion(flowKey, targetVersion)
                ?: throw IllegalArgumentException("版本不存在: $targetVersion")

        val newVersion = main.version + 1
        ensureVersionSlotFree(flowKey, newVersion)

        archiveActiveVersions(flowKey)
        val rollbackVersion =
            flowRepository.insertVersion(
                DecisionFlowVersion(
                    id = null,
                    flowId = main.id!!,
                    flowKey = flowKey,
                    version = newVersion,
                    flowGraph = target.flowGraph,
                    changeReason = "回滚到版本 $targetVersion",
                    changedBy = operator,
                    changedAt = Instant.now(),
                    isRollback = true,
                    rollbackFromVersion = targetVersion,
                    status = VERSION_STATUS_ACTIVE,
                ),
            )
        flowRepository.updateMainPointer(flowKey, newVersion, newVersion, target.flowGraph, operator)
        log.info(
            "决策流版本回滚: flowKey={}, targetVersion={}, newVersion={}, operator={}",
            flowKey,
            targetVersion,
            newVersion,
            operator,
        )
        // 回滚即生效：决策侧流图载荷缓存需立即失效
        eventPublisher.publishInvalidation(CacheInvalidationType.FLOW, flowKey)
        return toVersionResponse(rollbackVersion)
    }

    // ---------- 私有辅助 ----------

    /** 新版本号槽位防冲突（防并发双写 TOCTOU，消息风格与 2b 规则版本一致） */
    private fun ensureVersionSlotFree(
        flowKey: String,
        newVersion: Int,
    ) {
        if (flowRepository.findVersion(flowKey, newVersion) != null) {
            throw IllegalArgumentException("版本冲突：决策流 $flowKey 的版本 $newVersion 已存在，请重试")
        }
    }

    /** 归档当前全部 ACTIVE 版本行（发布/保存/回滚共用的前置动作） */
    private fun archiveActiveVersions(flowKey: String) {
        flowRepository
            .findVersionsByFlowKey(flowKey)
            .filter { it.status == VERSION_STATUS_ACTIVE }
            .forEach { flowRepository.updateVersionStatus(flowKey, it.version, VERSION_STATUS_ARCHIVED) }
    }

    private fun toResponse(main: DecisionFlowMain): DecisionFlowResponse =
        DecisionFlowResponse(
            id = main.id,
            flowKey = main.flowKey,
            flowName = main.flowName,
            flowDescription = main.flowDescription,
            flowGraph = main.flowGraph,
            version = main.version,
            status = main.status,
            createdBy = main.createdBy,
            createdAt = main.createdAt,
            updatedBy = main.updatedBy,
            updatedAt = main.updatedAt,
            enabled = main.enabled,
            optLockVersion = OPT_LOCK_VERSION_PLACEHOLDER,
            activeVersion = main.activeVersion,
            environmentId = main.environmentId,
        )

    private fun toVersionResponse(version: DecisionFlowVersion): DecisionFlowVersionResponse =
        DecisionFlowVersionResponse(
            id = version.id,
            flowId = version.flowId,
            flowKey = version.flowKey,
            version = version.version,
            flowGraph = version.flowGraph,
            changeReason = version.changeReason,
            changedBy = version.changedBy,
            changedAt = version.changedAt,
            isRollback = version.isRollback,
            rollbackFromVersion = version.rollbackFromVersion,
            status = version.status,
        )

    companion object {
        private val log = LoggerFactory.getLogger(DecisionFlowService::class.java)

        /** 决策流主表状态（V9 CHECK 约束取值） */
        private const val FLOW_STATUS_DRAFT = "DRAFT"
        private const val FLOW_STATUS_ACTIVE = "ACTIVE"
        private const val FLOW_STATUS_DELETED = "DELETED"

        /** 版本状态（V14 语义：DRAFT/CANARY/ACTIVE/ARCHIVED） */
        private const val VERSION_STATUS_DRAFT = "DRAFT"
        private const val VERSION_STATUS_CANARY = "CANARY"
        private const val VERSION_STATUS_ACTIVE = "ACTIVE"
        private const val VERSION_STATUS_ARCHIVED = "ARCHIVED"

        /** 旧 JPA @Version 遗留列占位值（新写入恒 0，与 2b RuleResponse 口径一致） */
        private const val OPT_LOCK_VERSION_PLACEHOLDER = 0L
    }
}
