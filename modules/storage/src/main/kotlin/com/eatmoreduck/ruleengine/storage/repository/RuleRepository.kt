package com.eatmoreduck.ruleengine.storage.repository

import com.eatmoreduck.ruleengine.domain.Rule

/**
 * 规则仓储（对应旧 RuleRepository + RuleLifecycleService 的查询职责）。
 *
 * 事务边界约定：实现不自行开启 Exposed 事务，由调用方的 Spring `@Transactional`
 * （经 SpringTransactionManager）或测试中的 `transaction { }` 提供事务上下文。
 */
interface RuleRepository {
    /** 物理删除指定 Key 的软删残留（主行 + 版本行）；同名重建前调用，保证同 Key 单套数据 */
    fun purgeDeleted(ruleKey: String)

    /**
     * 新增或更新规则行。
     * - [Rule.id] 为 null → 插入，返回携带生成 id 的副本
     * - [Rule.id] 非 null → 按 id 全列更新，目标行不存在时抛 [com.eatmoreduck.ruleengine.storage.EntityNotFoundException]
     */
    fun save(rule: Rule): Rule

    /** 按业务键查询规则（含已删除，调用方自行决定过滤） */
    fun findByRuleKey(ruleKey: String): Rule?

    fun existsByRuleKey(ruleKey: String): Boolean

    /** 加载启用中的规则（deleted=false），可按环境过滤；决策链路的规则装载入口 */
    fun findEnabled(environmentId: Long? = null): List<Rule>

    /** 组合条件分页查询（管理端列表）；条件全部可选，未指定即不过滤 */
    fun search(query: RuleSearchQuery): List<Rule>
}

/** 规则列表查询条件（对应旧 RuleLifecycleService 的 findByConditions 组合过滤） */
data class RuleSearchQuery(
    /** 关键字：命中 rule_key 或 rule_name（大小写不敏感的包含匹配） */
    val keyword: String? = null,
    /** NULL 表示不过滤团队（含全局资源） */
    val teamId: Long? = null,
    val environmentId: Long? = null,
    /** 是否包含已软删除规则（默认排除） */
    val includeDeleted: Boolean = false,
    val limit: Int = 100,
    val offset: Long = 0,
)
