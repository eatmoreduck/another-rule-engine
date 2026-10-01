package com.eatmoreduck.ruleengine.admin.analytics

import com.eatmoreduck.ruleengine.admin.analytics.RuleConflictDetector.Condition
import com.eatmoreduck.ruleengine.admin.dto.ConflictResultResponse
import com.eatmoreduck.ruleengine.domain.Rule
import com.eatmoreduck.ruleengine.domain.RuleStatus
import com.eatmoreduck.ruleengine.storage.repository.RuleRepository
import com.eatmoreduck.ruleengine.storage.repository.RuleSearchQuery
import com.eatmoreduck.ruleengine.storage.repository.RuleVersionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale

/**
 * 规则冲突检测服务（对应旧 RuleConflictDetector：TEST-03 条件冲突/决策冲突检测）。
 *
 * 算法语义忠实迁移（含判定边界）：
 * 1. 条件提取正则照搬旧 CONDITION_PATTERN（变量名 + 比较运算符 + 字面值）；
 * 2. CONDITION_CONFLICT（severity=HIGH）：两条规则对同一变量的条件互斥——
 *    数值可解析时判定互斥范围（`>`/`>=` 对 `<`/`<=` 且左值>=右值，反之亦然），
 *    或同值异判（`==` 对 `!=` 且字面值相等）；数值不可解析时仅做同值异判；
 * 3. DECISION_CONFLICT（severity=MEDIUM）：两脚本 PASS/REJECT 输出组合可能对相同输入
 *    产生不同决策（布尔判定照搬旧 hasConflictingDecisions 的六项析取）。
 *
 * 脚本来源差异说明：旧实现读 rules 主表 groovy_script 冗余列；新架构定义载荷收敛到
 * rule_versions，此处读当前最新内容版本（旧主表列在草稿创建与版本发布时同步更新，
 * 语义等价于最新内容版本），与 RuleService 暴露 groovyScript 的口径一致。
 */
@Service
class RuleConflictDetector(
    private val ruleRepository: RuleRepository,
    private val versionRepository: RuleVersionRepository,
) {
    /** 全量冲突检测：所有启用规则两两比对 */
    @Transactional(readOnly = true)
    fun detectAllConflicts(): List<ConflictResultResponse> {
        val rules = enabledRules()
        val conflicts = mutableListOf<ConflictResultResponse>()
        for (i in rules.indices) {
            for (j in i + 1 until rules.size) {
                conflicts += detectConflictBetween(rules[i], rules[j])
            }
        }
        return conflicts
    }

    /** 单规则冲突检测：目标规则（任意状态）与其他启用规则逐一比对 */
    @Transactional(readOnly = true)
    fun detectConflictsForRule(ruleKey: String): List<ConflictResultResponse> {
        val targetRule = ruleRepository.findByRuleKey(ruleKey) ?: return emptyList()
        val conflicts = mutableListOf<ConflictResultResponse>()
        for (other in enabledRules()) {
            if (other.ruleKey == targetRule.ruleKey) continue
            conflicts += detectConflictBetween(targetRule, other)
        }
        return conflicts
    }

    // ---------- 私有辅助 ----------

    /** 检测两条规则之间的冲突（条件互斥 + 决策组合矛盾） */
    private fun detectConflictBetween(
        rule1: Rule,
        rule2: Rule,
    ): List<ConflictResultResponse> {
        val conflicts = mutableListOf<ConflictResultResponse>()

        val script1 = currentScript(rule1.ruleKey)
        val script2 = currentScript(rule2.ruleKey)

        // 条件冲突：同一变量的互斥条件两两比对
        val conditions1 = extractConditions(script1)
        val conditions2 = extractConditions(script2)
        val sharedVars = conditions1.keys intersect conditions2.keys
        for (variable in sharedVars) {
            for (c1 in conditions1.getValue(variable)) {
                for (c2 in conditions2.getValue(variable)) {
                    if (isConflicting(c1, c2)) {
                        conflicts +=
                            ConflictResultResponse(
                                conflictType = CONDITION_CONFLICT,
                                ruleKey1 = rule1.ruleKey,
                                ruleName1 = rule1.ruleName,
                                ruleKey2 = rule2.ruleKey,
                                ruleName2 = rule2.ruleName,
                                description =
                                    String.format(
                                        Locale.ROOT,
                                        "变量 '%s' 存在冲突条件: 规则[%s] 中 %s, 规则[%s] 中 %s",
                                        variable,
                                        rule1.ruleKey,
                                        c1,
                                        rule2.ruleKey,
                                        c2,
                                    ),
                                severity = SEVERITY_HIGH,
                            )
                    }
                }
            }
        }

        // 决策冲突：相同条件但可能产生不同决策结果
        if (hasConflictingDecisions(script1, script2)) {
            conflicts +=
                ConflictResultResponse(
                    conflictType = DECISION_CONFLICT,
                    ruleKey1 = rule1.ruleKey,
                    ruleName1 = rule1.ruleName,
                    ruleKey2 = rule2.ruleKey,
                    ruleName2 = rule2.ruleName,
                    description =
                        String.format(
                            Locale.ROOT,
                            "规则[%s]和规则[%s]可能对相同输入产生不同决策结果",
                            rule1.ruleKey,
                            rule2.ruleKey,
                        ),
                    severity = SEVERITY_MEDIUM,
                )
        }

        return conflicts
    }

    /** 从脚本中提取条件（变量 → 条件列表；正则照搬旧 CONDITION_PATTERN） */
    internal fun extractConditions(script: String): Map<String, List<Condition>> {
        val conditions = linkedMapOf<String, MutableList<Condition>>()
        for (match in CONDITION_PATTERN.findAll(script)) {
            val condition = Condition(match.groupValues[1], match.groupValues[2], match.groupValues[3])
            conditions.getOrPut(condition.variable) { mutableListOf() } += condition
        }
        return conditions
    }

    /** 判断两个条件是否冲突（数值互斥范围 + 同值异判，边界照搬旧 isConflicting） */
    internal fun isConflicting(
        c1: Condition,
        c2: Condition,
    ): Boolean {
        if (c1.variable != c2.variable) return false

        val v1 = c1.value.toDoubleOrNull()
        val v2 = c2.value.toDoubleOrNull()
        if (v1 != null && v2 != null) {
            // 互斥范围：如 amount > 1000 与 amount < 500
            if ((c1.operator == ">" || c1.operator == ">=") && (c2.operator == "<" || c2.operator == "<=")) {
                return v1 >= v2
            }
            if ((c1.operator == "<" || c1.operator == "<=") && (c2.operator == ">" || c2.operator == ">=")) {
                return v1 <= v2
            }
            // 同值异判：如 amount == 500 与 amount != 500
            if (c1.operator == "==" && c2.operator == "!=" && c1.value == c2.value) {
                return true
            }
        } else {
            // 非数值字面量：仅做相等与不等的同值冲突
            if (c1.operator == "==" && c2.operator == "!=" && c1.value == c2.value) {
                return true
            }
        }
        return false
    }

    /**
     * 检测脚本中的决策冲突（照搬旧实现的布尔判定）：
     * 一方只 REJECT/只 PASS，另一方为对立单边或双分支时的六种组合。
     */
    internal fun hasConflictingDecisions(
        script1: String,
        script2: String,
    ): Boolean {
        val hasReject1 = script1.contains("REJECT")
        val hasPass1 = script1.contains("PASS")
        val hasReject2 = script2.contains("REJECT")
        val hasPass2 = script2.contains("PASS")

        val onlyReject1 = hasReject1 && !hasPass1
        val onlyPass1 = hasPass1 && !hasReject1
        val onlyReject2 = hasReject2 && !hasPass2
        val onlyPass2 = hasPass2 && !hasReject2

        return (onlyReject1 && onlyPass2) ||
            (onlyPass1 && onlyReject2) ||
            (onlyReject1 && hasReject2 && hasPass2) ||
            (onlyPass1 && hasReject2 && hasPass2) ||
            (onlyReject2 && hasReject1 && hasPass1) ||
            (onlyPass2 && hasReject1 && hasPass1)
    }

    /** 启用中的规则（旧 findByEnabledTrue：enabled=true 且未删除，按 id 升序） */
    private fun enabledRules(): List<Rule> =
        ruleRepository
            .search(RuleSearchQuery(includeDeleted = false, limit = MAX_SCAN))
            .filter { it.status == RuleStatus.ENABLED }

    /** 规则当前最新内容版本的载荷（无版本时视为空脚本） */
    private fun currentScript(ruleKey: String): String = versionRepository.findCurrentVersion(ruleKey)?.definitionJson ?: ""

    /** 条件数据类（对应旧 Condition；toString 参与冲突描述文案） */
    internal data class Condition(
        val variable: String,
        val operator: String,
        val value: String,
    ) {
        override fun toString(): String = "$variable $operator $value"
    }

    companion object {
        /**
         * 条件比较模式：提取变量名、操作符和值，
         * 匹配如 amount > 1000、age >= 18、status == "ACTIVE" 等（照搬旧 CONDITION_PATTERN）。
         */
        private val CONDITION_PATTERN = Regex("(\\w+)\\s*(>|>=|<|<=|==|!=)\\s*['\"]?([\\w.]+)['\"]?")

        private const val CONDITION_CONFLICT = "CONDITION_CONFLICT"
        private const val DECISION_CONFLICT = "DECISION_CONFLICT"
        private const val SEVERITY_HIGH = "HIGH"
        private const val SEVERITY_MEDIUM = "MEDIUM"

        /** 规则装载的最大扫描行数（与 RuleService 同一防护口径） */
        const val MAX_SCAN: Int = 10_000
    }
}
