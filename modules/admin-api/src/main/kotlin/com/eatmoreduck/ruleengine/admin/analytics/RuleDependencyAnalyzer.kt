package com.eatmoreduck.ruleengine.admin.analytics

import com.eatmoreduck.ruleengine.admin.dto.DependencyGraphResponse
import com.eatmoreduck.ruleengine.admin.dto.DependencyGraphResponse.DependencyEdge
import com.eatmoreduck.ruleengine.admin.dto.DependencyGraphResponse.DependencyNode
import com.eatmoreduck.ruleengine.domain.Rule
import com.eatmoreduck.ruleengine.domain.RuleStatus
import com.eatmoreduck.ruleengine.storage.repository.RuleRepository
import com.eatmoreduck.ruleengine.storage.repository.RuleSearchQuery
import com.eatmoreduck.ruleengine.storage.repository.RuleVersionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 规则依赖关系分析服务（对应旧 RuleDependencyAnalyzer：MON-04 特征依赖图）。
 *
 * 算法语义忠实迁移：
 * - 特征提取正则照搬旧 FEATURE_PATTERN（匹配 features.xxx / context.xxx / params.xxx
 *   / features['xxx'] 等变量引用，捕获词法名）；
 * - 节点为启用规则的 (ruleKey, ruleName, 特征列表, executionCount=0)
 *   （旧实现固定 0 未接日志，保持一致）；
 * - 边为两两规则共享特征非空时的 FEATURE_DEPENDENCY 连接，sharedFeatures 汇总全部共享特征。
 *
 * 脚本来源差异说明：旧实现读 rules 主表 groovy_script 冗余列；新架构定义载荷收敛到
 * rule_versions（主表列为占位空串），此处读当前最新内容版本，与 RuleService 暴露
 * groovyScript 的口径一致。
 */
@Service
class RuleDependencyAnalyzer(
    private val ruleRepository: RuleRepository,
    private val versionRepository: RuleVersionRepository,
) {
    /** 分析所有启用规则的依赖关系，返回完整依赖图 */
    @Transactional(readOnly = true)
    fun analyzeDependencies(): DependencyGraphResponse {
        val rules = enabledRules()
        val featuresByRule = rules.associate { it.ruleKey to extractFeatures(currentScript(it.ruleKey)) }

        val nodes =
            rules.map { rule ->
                DependencyNode(
                    ruleKey = rule.ruleKey,
                    ruleName = rule.ruleName,
                    features = featuresByRule[rule.ruleKey].orEmpty(),
                    executionCount = 0,
                )
            }

        val edges = mutableListOf<DependencyEdge>()
        val allSharedFeatures = linkedSetOf<String>()
        val ruleKeys = rules.map { it.ruleKey }
        for (i in ruleKeys.indices) {
            for (j in i + 1 until ruleKeys.size) {
                val shared = sharedFeatures(featuresByRule[ruleKeys[i]].orEmpty(), featuresByRule[ruleKeys[j]].orEmpty())
                if (shared.isNotEmpty()) {
                    allSharedFeatures += shared
                    edges += DependencyEdge(ruleKeys[i], ruleKeys[j], DEPENDENCY_TYPE, shared)
                }
            }
        }

        return DependencyGraphResponse(
            nodes = nodes,
            edges = edges,
            sharedFeatures = allSharedFeatures.toList(),
        )
    }

    /**
     * 分析指定规则的特征依赖（仅包含该规则及与其共享特征的其他启用规则）。
     * 规则不存在时返回空图（旧实现返回字段为 null 的空对象，前端类型声明为数组，此处给空集合更安全）。
     */
    @Transactional(readOnly = true)
    fun analyzeRuleDependencies(ruleKey: String): DependencyGraphResponse {
        val targetRule = ruleRepository.findByRuleKey(ruleKey) ?: return DependencyGraphResponse(emptyList(), emptyList(), emptyList())
        val targetFeatures = extractFeatures(currentScript(targetRule.ruleKey))

        val nodes = mutableListOf(DependencyNode(targetRule.ruleKey, targetRule.ruleName, targetFeatures, 0))
        val edges = mutableListOf<DependencyEdge>()
        for (otherRule in enabledRules()) {
            if (otherRule.ruleKey == targetRule.ruleKey) continue
            val otherFeatures = extractFeatures(currentScript(otherRule.ruleKey))
            val shared = sharedFeatures(targetFeatures, otherFeatures)
            if (shared.isNotEmpty()) {
                nodes += DependencyNode(otherRule.ruleKey, otherRule.ruleName, otherFeatures, 0)
                edges += DependencyEdge(targetRule.ruleKey, otherRule.ruleKey, DEPENDENCY_TYPE, shared)
            }
        }

        return DependencyGraphResponse(
            nodes = nodes,
            edges = edges,
            sharedFeatures = targetFeatures,
        )
    }

    // ---------- 私有辅助 ----------

    /** 启用中的规则（旧 findByEnabledTrue：enabled=true 且未删除，按 id 升序） */
    private fun enabledRules(): List<Rule> =
        ruleRepository
            .search(RuleSearchQuery(includeDeleted = false, limit = MAX_SCAN))
            .filter { it.status == RuleStatus.ENABLED }

    /** 规则当前最新内容版本的载荷（无版本时视为空脚本） */
    private fun currentScript(ruleKey: String): String = versionRepository.findCurrentVersion(ruleKey)?.definitionJson ?: ""

    /** 两个特征列表的交集（保持左侧声明顺序；两列表各自已去重，结果无重复） */
    private fun sharedFeatures(
        left: List<String>,
        right: List<String>,
    ): List<String> = left.filter { it in right }

    /** 从规则载荷中提取特征依赖（正则与去重口径照搬旧 extractFeatures 的 LinkedHashSet） */
    private fun extractFeatures(script: String): List<String> {
        val features = linkedSetOf<String>()
        for (match in FEATURE_PATTERN.findAll(script)) {
            features += match.groupValues[1]
        }
        return features.toList()
    }

    companion object {
        /**
         * 变量引用模式：匹配 Groovy 脚本中的变量引用，
         * 如 features.amount、features['age']、context.userId 等（照搬旧 FEATURE_PATTERN）。
         */
        private val FEATURE_PATTERN =
            Regex("(?:features|context|params)(?:\\[|\\.)['\"]?(\\w+)['\"]?\\]?")

        /** 旧实现唯一的依赖类型常量 */
        private const val DEPENDENCY_TYPE = "FEATURE_DEPENDENCY"

        /** 规则装载的最大扫描行数（与 RuleService 同一防护口径） */
        const val MAX_SCAN: Int = 10_000
    }
}
