package com.eatmoreduck.ruleengine.admin.importexport

import com.eatmoreduck.ruleengine.admin.dto.CreateRuleRequest
import com.eatmoreduck.ruleengine.admin.dto.ImportRulesResponse
import com.eatmoreduck.ruleengine.admin.dto.RuleExportData
import com.eatmoreduck.ruleengine.admin.rules.RuleAssembler
import com.eatmoreduck.ruleengine.admin.rules.RuleService
import com.eatmoreduck.ruleengine.domain.Rule
import com.eatmoreduck.ruleengine.storage.repository.RuleRepository
import com.eatmoreduck.ruleengine.storage.repository.RuleSearchQuery
import com.eatmoreduck.ruleengine.storage.repository.RuleVersionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 规则导入导出服务（响应结构照搬旧 RuleImportExportService）。
 *
 * 导出：规则主记录（[com.eatmoreduck.ruleengine.admin.dto.RuleResponse] 形态，脚本由当前
 * 版本载荷回填）+ 版本历史，打包为 [RuleExportData]。
 *
 * 导入：逐条解析 → ruleKey 已存在跳过 → 否则复用 [RuleService.createRule] 创建
 * （含沙箱校验与失效广播，不绕过任何校验）；源规则为禁用态时创建后再禁用，保持旧实现
 * "enabled 按源数据还原（deleted 恒不还原）"的语义。失败逐条收集进 failures，不中断其余记录。
 *
 * 与旧实现的事务语义差异：旧实现整个导入包在一个事务里（单条失败回滚会波及整批提交），
 * 新实现按记录独立提交——每条规则经 [RuleService] 代理调用持有独立事务，单条失败回滚
 * 不影响已导入记录，可观测行为（计数与 failures 明细）与旧契约一致。
 */
@Service
class RuleImportExportService(
    private val ruleRepository: RuleRepository,
    private val versionRepository: RuleVersionRepository,
    private val ruleService: RuleService,
    private val assembler: RuleAssembler,
) {
    /** 导出所有规则（含已软删除，口径同旧 findAll） */
    @Transactional(readOnly = true)
    fun exportAllRules(operator: String): RuleExportData {
        // search 的 includeDeleted 为"按 deleted 列过滤"语义，两次互补查询取全集
        val active = ruleRepository.search(RuleSearchQuery(includeDeleted = false, limit = MAX_SCAN))
        val deleted = ruleRepository.search(RuleSearchQuery(includeDeleted = true, limit = MAX_SCAN))
        val records = (active + deleted).map(::toExportRecord)
        log.info("导出所有规则: count={}", records.size)
        return toExportData(records, operator)
    }

    /** 导出单条规则（规则不存在抛 400 类异常，消息与旧实现一致） */
    @Transactional(readOnly = true)
    fun exportRule(
        ruleKey: String,
        operator: String,
    ): RuleExportData {
        val rule =
            ruleRepository.findByRuleKey(ruleKey)
                ?: throw IllegalArgumentException("规则不存在: $ruleKey")
        val records = listOf(toExportRecord(rule))
        log.info("导出规则: ruleKey={}", ruleKey)
        return toExportData(records, operator)
    }

    /** 批量导出规则（不存在的 ruleKey 静默跳过，口径同旧 findByRuleKeyIn） */
    @Transactional(readOnly = true)
    fun exportRules(
        ruleKeys: List<String>,
        operator: String,
    ): RuleExportData {
        val records = ruleKeys.mapNotNull(ruleRepository::findByRuleKey).map(::toExportRecord)
        log.info("批量导出规则: count={}", records.size)
        return toExportData(records, operator)
    }

    /**
     * 导入规则：逐条创建，已存在跳过、失败收集明细。
     *
     * 故意不加外层事务：单条创建失败若传播进共享事务会把整批标记 rollback-only，
     * 导致最终提交抛 UnexpectedRollbackException；按记录独立提交后计数语义与旧契约一致。
     */
    fun importRules(
        exportData: RuleExportData,
        operator: String,
    ): ImportRulesResponse {
        if (exportData.rules.isEmpty()) {
            return ImportRulesResponse.of(0, 0, 0, emptyList())
        }

        var importedCount = 0
        var skippedCount = 0
        var failedCount = 0
        val failures = mutableListOf<String>()

        for (record in exportData.rules) {
            val ruleData = record.rule
            try {
                if (ruleData.ruleKey.isBlank() || ruleData.ruleName.isBlank() || ruleData.groovyScript.isBlank()) {
                    throw IllegalArgumentException("缺少 ruleKey / ruleName / groovyScript 必填字段")
                }
                // ruleKey 冲突策略与旧实现一致：已存在直接跳过（不覆盖、不报错）。
                // 读走 RuleService 代理方法（自带只读事务）；getRule 仅在规则不存在时抛异常
                if (ruleExists(ruleData.ruleKey)) {
                    skippedCount++
                    continue
                }
                // 复用创建链路：唯一性守卫 + 沙箱校验 + 版本 1 落库 + 缓存失效广播
                ruleService.createRule(
                    CreateRuleRequest(
                        ruleKey = ruleData.ruleKey,
                        ruleName = ruleData.ruleName,
                        ruleDescription = ruleData.ruleDescription,
                        groovyScript = ruleData.groovyScript,
                    ),
                    operator,
                )
                // 源规则为禁用态时保持禁用（旧实现按源数据还原 enabled；deleted 恒不还原）
                if (!ruleData.enabled) {
                    ruleService.disableRule(ruleData.ruleKey, operator)
                }
                importedCount++
            } catch (e: Exception) {
                failedCount++
                failures += "规则 ${ruleData.ruleKey.ifBlank { "unknown" }} 导入失败: ${e.message}"
                log.error("导入规则失败: ruleKey={}", ruleData.ruleKey, e)
            }
        }

        log.info("导入规则完成: imported={}, skipped={}, failed={}", importedCount, skippedCount, failedCount)
        return ImportRulesResponse.of(importedCount, skippedCount, failedCount, failures)
    }

    // ---------- 私有辅助 ----------

    /** 规则存在性检查（经 RuleService 代理获取只读事务上下文） */
    private fun ruleExists(ruleKey: String): Boolean =
        try {
            ruleService.getRule(ruleKey)
            true
        } catch (_: com.eatmoreduck.ruleengine.storage.EntityNotFoundException) {
            false
        }

    /** 规则行 → 导出记录（主记录 + 版本历史降序；脚本载荷由最新版本回填） */
    private fun toExportRecord(rule: Rule): RuleExportData.RuleExportRecord {
        val versions = versionRepository.findByRuleKey(rule.ruleKey)
        return RuleExportData.RuleExportRecord(
            rule = assembler.toRuleResponse(rule, versions.firstOrNull()?.definitionJson),
            versions = versions.map(assembler::toVersionResponse),
        )
    }

    private fun toExportData(
        records: List<RuleExportData.RuleExportRecord>,
        operator: String,
    ): RuleExportData =
        RuleExportData(
            formatVersion = RuleExportData.FORMAT_VERSION,
            exportedAt = RuleExportData.nowExportedAt(),
            exportedBy = operator,
            rules = records,
        )

    companion object {
        private val log = LoggerFactory.getLogger(RuleImportExportService::class.java)

        /** 全量导出的最大扫描行数（管理端目录量级防护，口径同 RuleService.MAX_SCAN） */
        const val MAX_SCAN: Int = 10_000
    }
}
