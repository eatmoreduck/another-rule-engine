package com.example.ruleengine.admin.rules

import com.example.ruleengine.admin.cache.publishInvalidation
import com.example.ruleengine.admin.dto.CreateVersionRequest
import com.example.ruleengine.admin.dto.RollbackRequest
import com.example.ruleengine.admin.dto.VersionDiffResponse
import com.example.ruleengine.admin.dto.VersionResponse
import com.example.ruleengine.domain.RuleVersion
import com.example.ruleengine.domain.VersionStatus
import com.example.ruleengine.shared.cache.CacheInvalidationType
import com.example.ruleengine.storage.repository.RuleRepository
import com.example.ruleengine.storage.repository.RuleVersionRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 规则版本管理服务：版本列表/详情/新建/回滚/对比。
 *
 * 业务规则照搬旧 VersionManagementService（守卫消息逐字一致），行为经阶段 1 领域模型修正：
 * - 新建版本落为 DRAFT（旧实现把新脚本直接写进主表即刻生效），推进入库不推进生效，
 *   生效切换由灰度全量切换（complete）经发布守卫完成；
 * - 回滚不再是"历史行补标记"：以更高版本号落地目标内容副本（isRollback=true，
 *   rollbackFromVersion 记录回滚依据的目标版本号——旧实现把该字段错填为回滚前的当前版本，
 *   与"等于自身版本号"的领域不变式冲突），并立即发布生效（保持"回滚即恢复"的旧观感）；
 * - 旧实现的"当前版本防 TOCTOU 检查"在新模型下改为检查新版本号未被占用（见 createVersion）。
 */
@Service
class VersionService(
    private val ruleRepository: RuleRepository,
    private val versionRepository: RuleVersionRepository,
    private val payloadValidator: RulePayloadValidator,
    private val assembler: RuleAssembler,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /** 版本列表（版本号降序；与旧实现一致，不校验规则存在性，未知规则返回空列表） */
    @Transactional(readOnly = true)
    fun getVersions(ruleKey: String): List<VersionResponse> = versionRepository.findByRuleKey(ruleKey).map(assembler::toVersionResponse)

    /** 特定版本详情 */
    @Transactional(readOnly = true)
    fun getVersion(
        ruleKey: String,
        version: Int,
    ): VersionResponse =
        versionRepository
            .findByRuleKeyAndVersion(ruleKey, version)
            ?.let(assembler::toVersionResponse)
            ?: throw IllegalArgumentException("版本不存在: ruleKey=$ruleKey, version=$version")

    /**
     * 创建新版本（DRAFT）：推进最新内容版本号，不改变生效版本。
     */
    @Transactional
    fun createVersion(
        ruleKey: String,
        request: CreateVersionRequest,
        operator: String,
    ): VersionResponse {
        val rule =
            ruleRepository.findByRuleKey(ruleKey)
                ?: throw IllegalArgumentException("规则不存在: $ruleKey")

        // 定义载荷校验（旧实现未校验直接入库，属于缺陷，此处前移为保存前拒绝）
        when (val result = payloadValidator.validate(request.groovyScript)) {
            is PayloadValidation.Valid -> {
                Unit
            }

            is PayloadValidation.Invalid -> {
                throw IllegalArgumentException("Groovy脚本语法错误: ${result.detail}")
            }
        }

        val newVersionNumber = rule.currentVersion + 1
        // 防御性检查：新版本号不得已存在（防并发双写的 TOCTOU 竞态，语义同旧实现的当前版本检查）
        if (versionRepository.existsByRuleKeyAndVersion(ruleKey, newVersionNumber)) {
            throw IllegalArgumentException("版本冲突：规则 $ruleKey 的版本 $newVersionNumber 已存在，请重试")
        }

        val now = Instant.now()
        val draft =
            versionRepository.save(
                RuleVersion(
                    ruleKey = ruleKey,
                    version = newVersionNumber,
                    definitionJson = request.groovyScript,
                    status = VersionStatus.DRAFT,
                    changeReason = request.changeReason,
                    changedBy = operator,
                    changedAt = now,
                ),
            )
        ruleRepository.save(rule.bumpCurrentVersion(operator, now))

        log.info("创建规则新版本: ruleKey={}, version={}, operator={}", ruleKey, newVersionNumber, operator)
        return assembler.toVersionResponse(draft)
    }

    /**
     * 回滚到指定版本：以更高版本号落地目标内容副本并立即发布生效。
     */
    @Transactional
    fun rollbackToVersion(
        ruleKey: String,
        targetVersion: Int,
        request: RollbackRequest,
        operator: String,
    ): VersionResponse {
        val rule =
            ruleRepository.findByRuleKey(ruleKey)
                ?: throw IllegalArgumentException("规则不存在: $ruleKey")
        val targetVersionEntity =
            versionRepository.findByRuleKeyAndVersion(ruleKey, targetVersion)
                ?: throw IllegalArgumentException("目标版本不存在: $targetVersion")

        val currentVersion = rule.currentVersion
        val newVersionNumber = currentVersion + 1
        if (versionRepository.existsByRuleKeyAndVersion(ruleKey, newVersionNumber)) {
            throw IllegalArgumentException("版本冲突：规则 $ruleKey 的版本 $newVersionNumber 已存在")
        }

        val now = Instant.now()
        // 以更高版本号落地目标内容副本；rollbackFromVersion 记录回滚依据（目标）版本号
        val rollbackDraft =
            RuleVersion(
                ruleKey = ruleKey,
                version = newVersionNumber,
                definitionJson = targetVersionEntity.definitionJson,
                status = VersionStatus.DRAFT,
                changeReason = request.reason ?: "回滚到版本 $targetVersion",
                changedBy = operator,
                changedAt = now,
                isRollback = true,
                rollbackFromVersion = targetVersion,
            )
        // 回滚立即生效：发布新版本 + 归档旧生效版本 + 主表指针推进（保持旧"回滚即恢复"观感）
        versionRepository
            .findByStatus(ruleKey, VersionStatus.ACTIVE)
            .forEach { active -> versionRepository.save(active.archive()) }
        val published = versionRepository.save(rollbackDraft.publish())
        ruleRepository.save(
            rule
                .bumpCurrentVersion(operator, now)
                .activateVersion(newVersionNumber, operator, now),
        )

        log.info(
            "回滚规则版本: ruleKey={}, from={}, to={}, newVersion={}, operator={}",
            ruleKey,
            currentVersion,
            targetVersion,
            newVersionNumber,
            operator,
        )
        // 回滚即发布生效：主表生效版本指针已推进，决策侧需立即失效旧载荷缓存
        // （createVersion 落 DRAFT 不影响决策缓存，不发布）
        eventPublisher.publishInvalidation(CacheInvalidationType.RULE, ruleKey)
        return assembler.toVersionResponse(published)
    }

    /**
     * 版本对比（照搬旧 generateSimpleDiff 的简单差异文本）。
     */
    @Transactional(readOnly = true)
    fun compareVersions(
        ruleKey: String,
        version1: Int,
        version2: Int,
    ): VersionDiffResponse {
        val v1 =
            versionRepository.findByRuleKeyAndVersion(ruleKey, version1)
                ?: throw IllegalArgumentException("版本不存在: $version1")
        val v2 =
            versionRepository.findByRuleKeyAndVersion(ruleKey, version2)
                ?: throw IllegalArgumentException("版本不存在: $version2")
        return VersionDiffResponse(
            ruleKey = ruleKey,
            version1 = version1,
            version2 = version2,
            script1 = v1.definitionJson,
            script2 = v2.definitionJson,
            diff = generateSimpleDiff(v1.definitionJson, v2.definitionJson),
        )
    }

    /** 简单差异文本（与旧实现逐字一致） */
    private fun generateSimpleDiff(
        script1: String,
        script2: String,
    ): String {
        if (script1 == script2) {
            return "两个版本内容完全相同"
        }

        val maxLength = maxOf(script1.length, script2.length)
        var diffIndex = -1
        for (i in 0 until minOf(script1.length, script2.length)) {
            if (script1[i] != script2[i]) {
                diffIndex = i
                break
            }
        }

        if (diffIndex == -1) {
            return "内容长度不同: version1=${script1.length}, version2=${script2.length}"
        }

        val contextStart = maxOf(0, diffIndex - 50)
        val contextEnd = minOf(maxLength, diffIndex + 50)

        return "首次差异出现在位置 %d\n版本1: ...%s...\n版本2: ...%s...".format(
            diffIndex,
            script1.substring(contextStart, minOf(script1.length, contextEnd)),
            script2.substring(contextStart, minOf(script2.length, contextEnd)),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(VersionService::class.java)
    }
}
