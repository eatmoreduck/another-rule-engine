package com.example.ruleengine.service.analytics;

import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.model.dto.DependencyGraph;
import com.example.ruleengine.model.dto.DependencyGraph.DependencyNode;
import com.example.ruleengine.model.dto.DependencyGraph.DependencyEdge;
import com.example.ruleengine.repository.ExecutionLogRepository;
import com.example.ruleengine.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 规则依赖关系分析服务
 * MON-04: 分析规则之间的特征依赖和执行顺序依赖
 *
 * 功能：
 * 1. 分析规则之间的特征依赖
 * 2. 分析规则执行顺序依赖
 * 3. 返回依赖图数据（节点+边）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleDependencyAnalyzer {

    private final RuleRepository ruleRepository;
    private final ExecutionLogRepository executionLogRepository;

    /**
     * 变量引用模式：匹配 Groovy 脚本中的变量引用
     * 如 features.amount, features['age'], context.userId 等
     */
    private static final Pattern FEATURE_PATTERN =
        Pattern.compile("(?:features|context|params)(?:\\[|\\.)['\"]?(\\w+)['\"]?\\]?");

    /**
     * 分析所有规则的依赖关系
     * MON-04: 返回完整的依赖图数据
     *
     * @return 依赖图
     */
    public DependencyGraph analyzeDependencies() {
        List<Rule> rules = ruleRepository.findByEnabledTrue();

        // 1. 为每个规则提取特征依赖
        Map<String, List<String>> ruleFeatures = new LinkedHashMap<>();
        for (Rule rule : rules) {
            List<String> features = extractFeatures(rule.getGroovyScript());
            ruleFeatures.put(rule.getRuleKey(), features);
        }

        // 2. 构建节点
        List<DependencyNode> nodes = new ArrayList<>();
        for (Rule rule : rules) {
            List<String> features = ruleFeatures.getOrDefault(rule.getRuleKey(),
                Collections.emptyList());
            nodes.add(new DependencyNode(
                rule.getRuleKey(),
                rule.getRuleName(),
                features,
                0  // executionCount 可后续从日志获取
            ));
        }

        // 3. 构建边（共享特征的规则之间建立连接）
        List<DependencyEdge> edges = new ArrayList<>();
        Set<String> allSharedFeatures = new LinkedHashSet<>();

        List<String> ruleKeys = new ArrayList<>(ruleFeatures.keySet());
        for (int i = 0; i < ruleKeys.size(); i++) {
            for (int j = i + 1; j < ruleKeys.size(); j++) {
                String key1 = ruleKeys.get(i);
                String key2 = ruleKeys.get(j);

                Set<String> shared = new HashSet<>(ruleFeatures.get(key1));
                shared.retainAll(ruleFeatures.get(key2));

                if (!shared.isEmpty()) {
                    allSharedFeatures.addAll(shared);

                    edges.add(new DependencyEdge(
                        key1, key2,
                        "FEATURE_DEPENDENCY",
                        new ArrayList<>(shared)
                    ));
                }
            }
        }

        // 4. 构建依赖图
        DependencyGraph graph = new DependencyGraph();
        graph.setNodes(nodes);
        graph.setEdges(edges);
        graph.setSharedFeatures(new ArrayList<>(allSharedFeatures));

        return graph;
    }

    /**
     * 分析指定规则的特征依赖
     *
     * @param ruleKey 规则Key
     * @return 依赖图（仅包含该规则及其依赖）
     */
    public DependencyGraph analyzeRuleDependencies(String ruleKey) {
        Optional<Rule> ruleOpt = ruleRepository.findByRuleKey(ruleKey);
        if (ruleOpt.isEmpty()) {
            return new DependencyGraph();
        }

        Rule targetRule = ruleOpt.get();
        List<String> targetFeatures = extractFeatures(targetRule.getGroovyScript());

        // 查找共享特征的其他规则
        List<Rule> allRules = ruleRepository.findByEnabledTrue();
        List<DependencyNode> nodes = new ArrayList<>();
        List<DependencyEdge> edges = new ArrayList<>();

        // 添加目标规则节点
        nodes.add(new DependencyNode(
            targetRule.getRuleKey(),
            targetRule.getRuleName(),
            targetFeatures,
            0
        ));

        for (Rule otherRule : allRules) {
            if (otherRule.getRuleKey().equals(ruleKey)) {
                continue;
            }

            List<String> otherFeatures = extractFeatures(otherRule.getGroovyScript());
            Set<String> shared = new HashSet<>(targetFeatures);
            shared.retainAll(otherFeatures);

            if (!shared.isEmpty()) {
                nodes.add(new DependencyNode(
                    otherRule.getRuleKey(),
                    otherRule.getRuleName(),
                    otherFeatures,
                    0
                ));

                edges.add(new DependencyEdge(
                    ruleKey,
                    otherRule.getRuleKey(),
                    "FEATURE_DEPENDENCY",
                    new ArrayList<>(shared)
                ));
            }
        }

        DependencyGraph graph = new DependencyGraph();
        graph.setNodes(nodes);
        graph.setEdges(edges);
        graph.setSharedFeatures(targetFeatures);
        return graph;
    }

    /**
     * 从 Groovy 脚本中提取特征依赖
     */
    private List<String> extractFeatures(String script) {
        Set<String> features = new LinkedHashSet<>();
        Matcher matcher = FEATURE_PATTERN.matcher(script);

        while (matcher.find()) {
            features.add(matcher.group(1));
        }

        return new ArrayList<>(features);
    }
}
