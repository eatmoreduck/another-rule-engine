package com.example.ruleengine.admin.rules

import com.example.ruleengine.admin.dto.CreateRuleRequest
import com.example.ruleengine.admin.dto.PageResponse
import com.example.ruleengine.admin.dto.RuleQuery
import com.example.ruleengine.admin.dto.RuleReferenceResponse
import com.example.ruleengine.admin.dto.RuleResponse
import com.example.ruleengine.admin.dto.UpdateRuleRequest
import com.example.ruleengine.admin.grayscale.DecisionFlowSupportRepository
import com.example.ruleengine.domain.IllegalTransitionException
import com.example.ruleengine.domain.Rule
import com.example.ruleengine.domain.RuleStatus
import com.example.ruleengine.domain.RuleVersion
import com.example.ruleengine.domain.VersionStatus
import com.example.ruleengine.dsl.DslParser
import com.example.ruleengine.dsl.FlowGraph
import com.example.ruleengine.dsl.ParseResult
import com.example.ruleengine.dsl.RuleSetNodeData
import com.example.ruleengine.storage.repository.RuleRepository
import com.example.ruleengine.storage.repository.RuleSearchQuery
import com.example.ruleengine.storage.repository.RuleVersionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId

/**
 * 规则生命周期服务：CRUD + 启停 + 引用查询。
 *
 * 业务规则照搬旧 RuleLifecycleService（错误消息逐字一致），行为经阶段 1 领域模型修正：
 * - 创建规则即落版本 1（ACTIVE）：新架构中规则定义载荷收敛于 rule_versions，
 *   主表 groovy_script 已废弃，决策链路读取生效版本——"新建规则立即可用"的旧观感由
 *   版本 1 直接 ACTIVE 保持；
 * - 更新脚本创建的新版本为 DRAFT（旧实现直接改主表即刻生效），生效需经灰度全量切换发布，
 *   见汇报「行为差异点」；
 * - 启停对已处于目标状态的规则幂等（旧实现无条件改布尔列）。
 */
@Service
class RuleService(
    private val ruleRepository: RuleRepository,
    private val versionRepository: RuleVersionRepository,
    private val payloadValidator: RulePayloadValidator,
    private val assembler: RuleAssembler,
    private val decisionFlowSupportRepository: DecisionFlowSupportRepository,
) {
    /**
     * 创建规则。
     */
    @Transactional
    fun createRule(
        request: CreateRuleRequest,
        operator: String,
    ): RuleResponse {
        // 1. rule_key 唯一性
        if (ruleRepository.existsByRuleKey(request.ruleKey)) {
            throw IllegalArgumentException("规则Key已存在: ${request.ruleKey}")
        }
        // 2. 定义载荷校验（DSL JSON / Groovy 脚本两道校验链）
        requireValidPayload(request.groovyScript)
        // 3. 主表 + 版本 1（ACTIVE，立即可用）
        val now = Instant.now()
        val saved =
            ruleRepository.save(
                Rule(
                    ruleKey = request.ruleKey,
                    ruleName = request.ruleName,
                    ruleDescription = request.ruleDescription,
                    status = RuleStatus.ENABLED,
                    currentVersion = 1,
                    activeVersion = 1,
                    createdBy = operator,
                    createdAt = now,
                ),
            )
        versionRepository.save(
            RuleVersion(
                ruleKey = request.ruleKey,
                version = 1,
                definitionJson = request.groovyScript,
                status = VersionStatus.ACTIVE,
                changeReason = "创建规则",
                changedBy = operator,
                changedAt = now,
            ),
        )
        log.info("创建规则: ruleKey={}, operator={}", request.ruleKey, operator)
        return assembler.toRuleResponse(saved, request.groovyScript)
    }

    /**
     * 更新规则：仅元数据变更直接改名；脚本变更创建新 DRAFT 版本。
     */
    @Transactional
    fun updateRule(
        ruleKey: String,
        request: UpdateRuleRequest,
        operator: String,
    ): RuleResponse {
        val rule =
            ruleRepository.findByRuleKey(ruleKey)
                ?: throw IllegalArgumentException("规则不存在: $ruleKey")
        val currentPayload = versionRepository.findCurrentVersion(ruleKey)?.definitionJson

        if (request.groovyScript != null) {
            requireValidPayload(request.groovyScript)
        }

        // 仅元数据变更（脚本为空或与当前载荷一致）：不创建新版本
        if (request.groovyScript == null || request.groovyScript == currentPayload) {
            val renamed = applyMetadata(rule, request, operator)
            ruleRepository.save(renamed)
            log.info("更新规则元数据: ruleKey={}, operator={}", ruleKey, operator)
            return assembler.toRuleResponse(renamed, currentPayload)
        }

        // 脚本变更：创建新 DRAFT 版本并推进最新内容版本号
        val now = Instant.now()
        val newVersionNumber = rule.currentVersion + 1
        if (versionRepository.existsByRuleKeyAndVersion(ruleKey, newVersionNumber)) {
            throw IllegalArgumentException("版本冲突：规则 $ruleKey 的版本 $newVersionNumber 已存在，请重试")
        }
        versionRepository.save(
            RuleVersion(
                ruleKey = ruleKey,
                version = newVersionNumber,
                definitionJson = request.groovyScript,
                status = VersionStatus.DRAFT,
                changeReason = request.changeReason ?: "更新规则脚本",
                changedBy = operator,
                changedAt = now,
            ),
        )
        val bumped = rule.bumpCurrentVersion(operator, now)
        val saved = ruleRepository.save(applyMetadata(bumped, request, operator))
        log.info("更新规则并创建新版本: ruleKey={}, newVersion={}, operator={}", ruleKey, newVersionNumber, operator)
        return assembler.toRuleResponse(saved, request.groovyScript)
    }

    /**
     * 软删除规则（旧删除守卫语义：存在且未删除即可删）。
     */
    @Transactional
    fun deleteRule(
        ruleKey: String,
        operator: String,
    ) {
        val rule =
            ruleRepository.findByRuleKey(ruleKey)
                ?: throw IllegalArgumentException("规则不存在: $ruleKey")
        if (rule.status == RuleStatus.DELETED) {
            throw IllegalStateException("规则正在使用中，不能删除: $ruleKey")
        }
        ruleRepository.save(rule.softDelete(operator, Instant.now()))
        log.info("删除规则: ruleKey={}, operator={}", ruleKey, operator)
    }

    /** 启用规则（幂等：已启用直接返回） */
    @Transactional
    fun enableRule(
        ruleKey: String,
        operator: String,
    ): RuleResponse {
        val rule =
            ruleRepository.findByRuleKey(ruleKey)
                ?: throw IllegalArgumentException("规则不存在: $ruleKey")
        if (rule.status == RuleStatus.ENABLED) {
            return assembler.toRuleResponse(rule, currentPayload(ruleKey))
        }
        val enabled =
            try {
                rule.enable(operator, Instant.now())
            } catch (e: IllegalTransitionException) {
                throw IllegalArgumentException(e.message, e)
            }
        val saved = ruleRepository.save(enabled)
        log.info("启用规则: ruleKey={}, operator={}", ruleKey, operator)
        return assembler.toRuleResponse(saved, currentPayload(ruleKey))
    }

    /** 禁用规则（幂等：已禁用直接返回） */
    @Transactional
    fun disableRule(
        ruleKey: String,
        operator: String,
    ): RuleResponse {
        val rule =
            ruleRepository.findByRuleKey(ruleKey)
                ?: throw IllegalArgumentException("规则不存在: $ruleKey")
        if (rule.status == RuleStatus.DISABLED) {
            return assembler.toRuleResponse(rule, currentPayload(ruleKey))
        }
        val disabled =
            try {
                rule.disable(operator, Instant.now())
            } catch (e: IllegalTransitionException) {
                throw IllegalArgumentException(e.message, e)
            }
        val saved = ruleRepository.save(disabled)
        log.info("禁用规则: ruleKey={}, operator={}", ruleKey, operator)
        return assembler.toRuleResponse(saved, currentPayload(ruleKey))
    }

    /** 规则详情 */
    @Transactional(readOnly = true)
    fun getRule(ruleKey: String): RuleResponse {
        val rule =
            ruleRepository.findByRuleKey(ruleKey)
                ?: throw IllegalArgumentException("规则不存在: $ruleKey")
        return assembler.toRuleResponse(rule, currentPayload(ruleKey))
    }

    /**
     * 规则列表（分页 + 关键字 + 启用过滤 + 是否含已删除）。
     *
     * 分页说明：storage 仓储的组合查询不含 count，管理端目录量级内
     * （[MAX_SCAN] 上限）采用大页拉取 + 内存过滤 + 内存分页，totalElements 精确。
     */
    @Transactional(readOnly = true)
    fun listRules(
        page: Int,
        size: Int,
        showDeleted: Boolean,
        keyword: String?,
        enabled: Boolean?,
    ): PageResponse<RuleResponse> {
        val rules =
            ruleRepository
                .search(
                    RuleSearchQuery(
                        keyword = keyword,
                        includeDeleted = showDeleted,
                        limit = MAX_SCAN,
                    ),
                ).asSequence()
                .filter { enabled == null || (it.status == RuleStatus.ENABLED) == enabled }
                .map { it to currentPayload(it.ruleKey) }
                .map { (rule, payload) -> assembler.toRuleResponse(rule, payload) }
                .toList()
        return PageResponse.of(rules, page, size)
    }

    /**
     * 多条件查询（对应旧 POST /rules/query：createdBy / enabled / 关键字 / 时间范围）。
     * 旧查询恒定排除已删除规则。
     */
    @Transactional(readOnly = true)
    fun queryRules(
        query: RuleQuery,
        page: Int,
        size: Int,
    ): PageResponse<RuleResponse> {
        val zone = ZoneId.systemDefault()
        val createdAtStart = query.createdAtStart?.atZone(zone)?.toInstant()
        val createdAtEnd = query.createdAtEnd?.atZone(zone)?.toInstant()
        val updatedAtStart = query.updatedAtStart?.atZone(zone)?.toInstant()
        val updatedAtEnd = query.updatedAtEnd?.atZone(zone)?.toInstant()
        val rules =
            ruleRepository
                .search(
                    RuleSearchQuery(
                        keyword = query.keyword,
                        includeDeleted = false,
                        limit = MAX_SCAN,
                    ),
                ).asSequence()
                .filter { query.createdBy == null || it.createdBy == query.createdBy }
                .filter { query.enabled == null || (it.status == RuleStatus.ENABLED) == query.enabled }
                .filter { createdAtStart == null || !it.createdAt.isBefore(createdAtStart) }
                .filter { createdAtEnd == null || !it.createdAt.isAfter(createdAtEnd) }
                .filter { rule ->
                    val updatedAt = rule.updatedAt
                    updatedAtStart == null || (updatedAt != null && !updatedAt.isBefore(updatedAtStart))
                }.filter { rule ->
                    val updatedAt = rule.updatedAt
                    updatedAtEnd == null || (updatedAt != null && !updatedAt.isAfter(updatedAtEnd))
                }.map { it to currentPayload(it.ruleKey) }
                .map { (rule, payload) -> assembler.toRuleResponse(rule, payload) }
                .toList()
        return PageResponse.of(rules, page, size)
    }

    /**
     * 查询规则被哪些决策流引用（扫描决策流图的规则集节点 ruleKeys，行为照搬旧 Controller）。
     */
    @Transactional(readOnly = true)
    fun getRuleReferences(ruleKey: String): List<RuleReferenceResponse> {
        val references = mutableListOf<RuleReferenceResponse>()
        for (flow in decisionFlowSupportRepository.findAllFlowMains()) {
            val graph = parseFlowGraphSafely(flow.flowGraph, flow.flowKey) ?: continue
            val referenced =
                graph.nodes.any { node ->
                    val keys = (node.data as? RuleSetNodeData)?.ruleKeys.orEmpty()
                    keys.any { it == ruleKey }
                }
            if (referenced) {
                references +=
                    RuleReferenceResponse(
                        type = "decision_flow",
                        id = flow.id,
                        name = flow.flowName,
                        key = flow.flowKey,
                    )
            }
        }
        return references
    }

    // ---------- 私有辅助 ----------

    /** 定义载荷校验：失败抛 400 类异常，消息前缀与旧实现一致 */
    private fun requireValidPayload(payload: String) {
        when (val result = payloadValidator.validate(payload)) {
            is PayloadValidation.Valid -> {
                Unit
            }

            is PayloadValidation.Invalid -> {
                throw IllegalArgumentException("Groovy脚本语法错误: ${result.detail}")
            }
        }
    }

    private fun currentPayload(ruleKey: String): String? = versionRepository.findCurrentVersion(ruleKey)?.definitionJson

    private fun applyMetadata(
        rule: Rule,
        request: UpdateRuleRequest,
        operator: String,
    ): Rule =
        rule.rename(
            ruleName = request.ruleName ?: rule.ruleName,
            ruleDescription = request.ruleDescription ?: rule.ruleDescription,
            operator = operator,
            at = Instant.now(),
        )

    private fun parseFlowGraphSafely(
        flowGraph: String,
        flowKey: String,
    ): FlowGraph? =
        when (val parsed = DslParser.parseFlowGraph(flowGraph)) {
            is ParseResult.Success -> {
                parsed.value
            }

            is ParseResult.Failure -> {
                log.warn("解析决策流定义失败: flowKey={}, error={}", flowKey, parsed.error.reason)
                null
            }
        }

    companion object {
        private val log = LoggerFactory.getLogger(RuleService::class.java)

        /** 内存分页的最大扫描行数（管理端目录量级防护） */
        const val MAX_SCAN: Int = 10_000
    }
}
