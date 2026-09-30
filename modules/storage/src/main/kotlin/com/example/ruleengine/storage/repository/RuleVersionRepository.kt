package com.example.ruleengine.storage.repository

import com.example.ruleengine.domain.RuleVersion
import com.example.ruleengine.domain.VersionStatus

/**
 * 规则版本仓储（对应旧 RuleVersionRepository 的派生查询职责）。
 *
 * 领域模型以 ruleKey 逻辑关联规则（不携带 rule_id）；rule_versions.rule_id NOT NULL
 * 的列由实现负责解析（查 rules 表），规则不存在时抛
 * [com.example.ruleengine.storage.EntityNotFoundException]。
 */
interface RuleVersionRepository {
    /**
     * 按 (ruleKey, version) 业务键 upsert 版本行：
     * - 不存在 → 插入
     * - 已存在 → 全列更新（发布/归档等状态推进的落库路径）
     */
    fun save(version: RuleVersion): RuleVersion

    fun findByRuleKeyAndVersion(
        ruleKey: String,
        version: Int,
    ): RuleVersion?

    fun existsByRuleKeyAndVersion(
        ruleKey: String,
        version: Int,
    ): Boolean

    /** 全部版本，按版本号降序（旧 findByRuleKeyOrderByVersionDesc） */
    fun findByRuleKey(ruleKey: String): List<RuleVersion>

    /** 最新内容版本（版本号最大者）；无版本返回 null */
    fun findCurrentVersion(ruleKey: String): RuleVersion?

    /** 当前生效版本（status=ACTIVE，版本号最大者）；无生效版本返回 null */
    fun findActiveVersion(ruleKey: String): RuleVersion?

    /** 指定规则处于 [status] 的全部版本，按版本号降序（发布归档旧 ACTIVE 的批量来源） */
    fun findByStatus(
        ruleKey: String,
        status: VersionStatus,
    ): List<RuleVersion>
}
