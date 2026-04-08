package com.example.ruleengine.service.test;

import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.model.dto.ConflictResult;
import com.example.ruleengine.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则冲突检测服务
 * TEST-03: 检测规则之间的条件冲突和矛盾
 *
 * 功能：
 * 1. 检测同一特征的冲突条件（如：规则A: amount>1000→REJECT, 规则B: amount>1000→PASS）
 * 2. 检测矛盾规则（互斥条件）
 * 3. 返回冲突列表
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleConflictDetector {

    private final RuleRepository ruleRepository;

    /**
     * 条件比较模式：提取变量名、操作符和值
     * 匹配如：amount > 1000, age >= 18, status == "ACTIVE" 等
     */
    private static final Pattern CONDITION_PATTERN =
        Pattern.compile("(\\w+)\\s*(>|>=|<|<=|==|!=)\\s*['\"]?([\\w.]+)['\"]?");

    /**
     * 全量冲突检测
     * TEST-03: 检测所有启用规则之间的冲突
     *
     * @return 冲突结果列表
     */
    public List<ConflictResult> detectAllConflicts() {
        List<Rule> rules = ruleRepository.findByEnabledTrue();
        return detectConflicts(rules);
    }

    /**
     * 单规则冲突检测
     * TEST-03: 检测指定规则与其他规则的冲突
     *
     * @param ruleKey 规则Key
     * @return 冲突结果列表
     */
    public List<ConflictResult> detectConflictsForRule(String ruleKey) {
        Optional<Rule> targetRuleOpt = ruleRepository.findByRuleKey(ruleKey);
        if (targetRuleOpt.isEmpty()) {
            return Collections.emptyList();
        }

        Rule targetRule = targetRuleOpt.get();
        List<Rule> allRules = ruleRepository.findByEnabledTrue();

        // 过滤出除目标规则外的其他规则
        List<Rule> otherRules = new ArrayList<>();
        for (Rule r : allRules) {
            if (!r.getRuleKey().equals(ruleKey)) {
                otherRules.add(r);
            }
        }

        List<Rule> targetList = List.of(targetRule);
        return detectConflictsBetween(targetList, otherRules);
    }

    /**
     * 在规则集合中检测冲突
     */
    private List<ConflictResult> detectConflicts(List<Rule> rules) {
        List<ConflictResult> conflicts = new ArrayList<>();

        for (int i = 0; i < rules.size(); i++) {
            for (int j = i + 1; j < rules.size(); j++) {
                Rule rule1 = rules.get(i);
                Rule rule2 = rules.get(j);

                List<ConflictResult> pairConflicts = detectConflictBetween(rule1, rule2);
                conflicts.addAll(pairConflicts);
            }
        }

        return conflicts;
    }

    /**
     * 在两组规则之间检测冲突
     */
    private List<ConflictResult> detectConflictsBetween(List<Rule> group1,
                                                         List<Rule> group2) {
        List<ConflictResult> conflicts = new ArrayList<>();

        for (Rule r1 : group1) {
            for (Rule r2 : group2) {
                List<ConflictResult> pairConflicts = detectConflictBetween(r1, r2);
                conflicts.addAll(pairConflicts);
            }
        }

        return conflicts;
    }

    /**
     * 检测两条规则之间的冲突
     */
    private List<ConflictResult> detectConflictBetween(Rule rule1, Rule rule2) {
        List<ConflictResult> conflicts = new ArrayList<>();

        // 提取条件
        Map<String, List<Condition>> conditions1 = extractConditions(rule1.getGroovyScript());
        Map<String, List<Condition>> conditions2 = extractConditions(rule2.getGroovyScript());

        // 查找共享变量
        Set<String> sharedVars = new HashSet<>(conditions1.keySet());
        sharedVars.retainAll(conditions2.keySet());

        for (String var : sharedVars) {
            List<Condition> conds1 = conditions1.get(var);
            List<Condition> conds2 = conditions2.get(var);

            for (Condition c1 : conds1) {
                for (Condition c2 : conds2) {
                    if (isConflicting(c1, c2)) {
                        String description = String.format(
                            "变量 '%s' 存在冲突条件: 规则[%s] 中 %s, 规则[%s] 中 %s",
                            var, rule1.getRuleKey(), c1, rule2.getRuleKey(), c2);

                        conflicts.add(new ConflictResult(
                            "CONDITION_CONFLICT",
                            rule1.getRuleKey(), rule1.getRuleName(),
                            rule2.getRuleKey(), rule2.getRuleName(),
                            description,
                            "HIGH"
                        ));
                    }
                }
            }
        }

        // 检测互斥决策冲突（相同条件但不同决策结果）
        if (hasConflictingDecisions(rule1.getGroovyScript(), rule2.getGroovyScript())) {
            conflicts.add(new ConflictResult(
                "DECISION_CONFLICT",
                rule1.getRuleKey(), rule1.getRuleName(),
                rule2.getRuleKey(), rule2.getRuleName(),
                String.format("规则[%s]和规则[%s]可能对相同输入产生不同决策结果",
                    rule1.getRuleKey(), rule2.getRuleKey()),
                "MEDIUM"
            ));
        }

        return conflicts;
    }

    /**
     * 从脚本中提取条件
     */
    Map<String, List<Condition>> extractConditions(String script) {
        Map<String, List<Condition>> conditions = new HashMap<>();
        Matcher matcher = CONDITION_PATTERN.matcher(script);

        while (matcher.find()) {
            String variable = matcher.group(1);
            String operator = matcher.group(2);
            String value = matcher.group(3);

            conditions.computeIfAbsent(variable, k -> new ArrayList<>())
                .add(new Condition(variable, operator, value));
        }

        return conditions;
    }

    /**
     * 判断两个条件是否冲突
     */
    private boolean isConflicting(Condition c1, Condition c2) {
        if (!c1.variable.equals(c2.variable)) {
            return false;
        }

        try {
            double v1 = Double.parseDouble(c1.value);
            double v2 = Double.parseDouble(c2.value);

            // 检测互斥范围：如 amount > 1000 和 amount < 500
            if ((c1.operator.equals(">") || c1.operator.equals(">="))
                && (c2.operator.equals("<") || c2.operator.equals("<="))) {
                return v1 >= v2;
            }
            if ((c1.operator.equals("<") || c1.operator.equals("<="))
                && (c2.operator.equals(">") || c2.operator.equals(">="))) {
                return v1 <= v2;
            }

            // 检测相同值但不同判断：如 amount > 1000 和 amount == 500
            if (c1.operator.equals("==") && (c2.operator.equals("!=") )
                && c1.value.equals(c2.value)) {
                return true;
            }

        } catch (NumberFormatException e) {
            // 非数值比较，检查相等和不等
            if (c1.operator.equals("==") && c2.operator.equals("!=")
                && c1.value.equals(c2.value)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检测脚本中的决策冲突
     * 简化实现：检查两个脚本是否包含不同的决策输出
     */
    private boolean hasConflictingDecisions(String script1, String script2) {
        boolean hasReject1 = script1.contains("REJECT");
        boolean hasPass1 = script1.contains("PASS");
        boolean hasReject2 = script2.contains("REJECT");
        boolean hasPass2 = script2.contains("PASS");

        // 两个脚本如果包含不同的决策组合，对相同输入可能产生不同结果
        // - 一个只 PASS，另一个只 REJECT（完全矛盾）
        // - 一个只 PASS/只 REJECT，另一个同时有 PASS 和 REJECT（条件分支不同）
        boolean onlyReject1 = hasReject1 && !hasPass1;
        boolean onlyPass1 = hasPass1 && !hasReject1;
        boolean onlyReject2 = hasReject2 && !hasPass2;
        boolean onlyPass2 = hasPass2 && !hasReject2;

        return (onlyReject1 && onlyPass2)
            || (onlyPass1 && onlyReject2)
            || (onlyReject1 && hasReject2 && hasPass2)
            || (onlyPass1 && hasReject2 && hasPass2)
            || (onlyReject2 && hasReject1 && hasPass1)
            || (onlyPass2 && hasReject1 && hasPass1);
    }

    /**
     * 条件数据类
     */
    static class Condition {
        String variable;
        String operator;
        String value;

        Condition(String variable, String operator, String value) {
            this.variable = variable;
            this.operator = operator;
            this.value = value;
        }

        @Override
        public String toString() {
            return variable + " " + operator + " " + value;
        }
    }
}
