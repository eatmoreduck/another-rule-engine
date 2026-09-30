package com.example.ruleengine.admin.environment

import com.example.ruleengine.admin.dto.CloneEnvironmentRequest
import com.example.ruleengine.admin.dto.CloneEnvironmentResponse
import com.example.ruleengine.admin.dto.EnvironmentResponse
import com.example.ruleengine.admin.dto.RuleResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 环境隔离服务：环境列表 / 详情 / 环境下规则 / 环境间规则克隆。
 *
 * 业务规则照搬旧 EnvironmentService。行为差异（本批有意为之，见汇报）：
 * - 恒启用：旧实现的 @ConditionalOnProperty(multi-environment.enabled) 在旧默认配置
 *   为 false（接口整体 404），新实现无条件装配使环境页可用；
 * - 克隆的跨环境同键写入不再触发唯一约束 500：rules.rule_key 全局 UNIQUE（V1 起即如此），
 *   "同键插入新环境行"必然违反约束（旧实现的复制路径在现实 schema 下全部 500）；
 *   写入前探测键的全局占用，冲突行计为跳过——克隆结果对现实数据（规则未挂环境）
 *   与旧实现一致（复制 0 条），对挂了环境的规则收敛为优雅跳过。
 */
@Service
class EnvironmentService(
    private val environmentRepository: EnvironmentRepository,
    private val ruleSupportRepository: EnvironmentRuleSupportRepository,
) {
    /** 环境列表（id 升序） */
    @Transactional(readOnly = true)
    fun listEnvironments(): List<EnvironmentResponse> = environmentRepository.findAll().map(::toResponse)

    /** 环境详情 */
    @Transactional(readOnly = true)
    fun getEnvironment(id: Long): EnvironmentResponse =
        environmentRepository
            .findById(id)
            ?.let(::toResponse)
            ?: throw IllegalArgumentException("环境不存在: $id")

    /** 环境下的规则（含禁用与软删，照旧；响应形状复用 2b 的 RuleResponse） */
    @Transactional(readOnly = true)
    fun getRulesByEnvironment(id: Long): List<RuleResponse> {
        val env =
            environmentRepository.findById(id)
                ?: throw IllegalArgumentException("环境不存在: $id")
        return ruleSupportRepository.findByEnvironmentId(env.id).map(::toRuleResponse)
    }

    /**
     * 克隆环境规则（from/to 为环境名称，前端 EnvironmentPage 传 name）：
     * 覆盖模式更新目标同名规则，非覆盖跳过；跨环境同键无法插入（全局唯一）时计为跳过。
     */
    @Transactional
    fun cloneEnvironmentRules(
        fromEnvName: String,
        toEnvName: String,
        overwrite: Boolean,
        operator: String,
    ): CloneEnvironmentResponse {
        val fromEnv =
            environmentRepository.findByName(fromEnvName)
                ?: throw IllegalArgumentException("环境不存在: $fromEnvName")
        val toEnv =
            environmentRepository.findByName(toEnvName)
                ?: throw IllegalArgumentException("环境不存在: $toEnvName")

        val sourceRules = ruleSupportRepository.findByEnvironmentId(fromEnv.id)
        val targetRules = ruleSupportRepository.findByEnvironmentId(toEnv.id)
        val targetRulesByKey = targetRules.associateBy { it.ruleKey }
        val targetRuleKeys = targetRulesByKey.keys

        var clonedCount = 0
        var skippedCount = 0

        for (sourceRule in sourceRules) {
            val existsInTarget = sourceRule.ruleKey in targetRuleKeys

            if (existsInTarget && !overwrite) {
                skippedCount++
                continue
            }

            if (existsInTarget && overwrite) {
                // 覆盖模式：更新目标环境同名规则的内容列（列集照旧 overwrite 分支）
                targetRulesByKey[sourceRule.ruleKey]?.let { existing ->
                    ruleSupportRepository.updateRuleContent(existing.id, sourceRule, operator)
                }
                clonedCount++
            } else {
                // 新建副本：rule_key 全局唯一（V1 即如此），键已被任何行占用（含源行自身——
                // 跨环境同键复制必然违反约束，旧实现此处 500）时计为跳过
                if (ruleSupportRepository.existsRuleKeyAnyRow(sourceRule.ruleKey)) {
                    skippedCount++
                    continue
                }
                ruleSupportRepository.insertEnvironmentCopy(sourceRule, toEnv.id, operator)
                clonedCount++
            }
        }

        log.info(
            "克隆环境规则: from={}, to={}, cloned={}, skipped={}",
            fromEnvName,
            toEnvName,
            clonedCount,
            skippedCount,
        )
        return CloneEnvironmentResponse.success(clonedCount, skippedCount)
    }

    // ---------- 私有辅助 ----------

    private fun toResponse(row: EnvironmentRow): EnvironmentResponse =
        EnvironmentResponse(
            id = row.id,
            name = row.name,
            type = row.type,
            description = row.description,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
        )

    private fun toRuleResponse(row: EnvironmentRuleRow): RuleResponse =
        RuleResponse(
            id = row.id,
            ruleKey = row.ruleKey,
            ruleName = row.ruleName,
            ruleDescription = row.ruleDescription,
            groovyScript = row.groovyScript,
            version = row.version,
            createdBy = row.createdBy,
            createdAt = row.createdAt,
            updatedBy = row.updatedBy,
            updatedAt = row.updatedAt,
            enabled = row.enabled,
            deleted = row.deleted,
            optLockVersion = 0L,
            activeVersion = null,
            environmentId = row.environmentId,
            teamId = null,
        )

    companion object {
        private val log = LoggerFactory.getLogger(EnvironmentService::class.java)
    }
}
