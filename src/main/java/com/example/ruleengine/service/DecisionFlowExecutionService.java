package com.example.ruleengine.service;

import com.example.ruleengine.cache.DecisionFlowCacheService;
import com.example.ruleengine.cache.RuleCacheService;
import com.example.ruleengine.constants.RuleStatus;
import com.example.ruleengine.domain.DecisionFlow;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.engine.GroovyScriptEngine;
import com.example.ruleengine.model.DecisionResponse;
import com.example.ruleengine.model.flow.FlowEdgeDef;
import com.example.ruleengine.model.flow.FlowGraph;
import com.example.ruleengine.model.flow.FlowNodeDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 决策流执行引擎服务
 * 负责解析流程图 JSON 并执行节点遍历决策
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DecisionFlowExecutionService {

    private final DecisionFlowCacheService flowCacheService;
    private final RuleCacheService ruleCacheService;
    private final GroovyScriptEngine scriptEngine;
    private final ObjectMapper objectMapper;

    /**
     * 执行决策流
     *
     * @param flowKey  决策流Key
     * @param features 特征数据
     * @return 决策响应
     */
    public DecisionResponse executeFlow(String flowKey, Map<String, Object> features) {
        long startTime = System.currentTimeMillis();

        try {
            DecisionFlow flow = flowCacheService.getEnabledFlow(flowKey);
            if (flow == null) {
                return DecisionResponse.builder()
                        .decision("REJECT")
                        .reason("决策流不存在或未启用")
                        .executionTimeMs(System.currentTimeMillis() - startTime)
                        .timeout(false)
                        .build();
            }

            FlowGraph graph = objectMapper.readValue(flow.getFlowGraph(), FlowGraph.class);
            FlowNodeDef startNode = graph.getNodes().stream()
                    .filter(n -> "start".equals(n.getType()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("决策流没有开始节点"));

            DecisionResponse response = traverseNode(startNode, graph, features);
            response.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            return response;

        } catch (Exception e) {
            log.error("决策流执行失败: flowKey={}", flowKey, e);
            return DecisionResponse.builder()
                    .decision("REJECT")
                    .reason("决策流执行失败: " + e.getMessage())
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .timeout(false)
                    .build();
        }
    }

    /**
     * 递归遍历流程节点
     */
    private DecisionResponse traverseNode(FlowNodeDef node, FlowGraph graph, Map<String, Object> features) {
        switch (node.getType()) {
            case "start": {
                FlowNodeDef next = getNextNode(node.getId(), graph, null);
                return next != null ? traverseNode(next, graph, features) : defaultResponse("无后续节点");
            }
            case "condition": {
                return evaluateCondition(node, graph, features);
            }
            case "ruleset": {
                return evaluateRuleSet(node, graph, features);
            }
            case "action": {
                Map<String, Object> data = node.getData();
                String action = data.get("action") != null ? data.get("action").toString() : "REJECT";
                String reason = data.get("reason") != null ? data.get("reason").toString() : "决策节点";
                return DecisionResponse.builder()
                        .decision(action)
                        .reason(reason)
                        .executionTimeMs(0L)
                        .timeout(false)
                        .build();
            }
            case "end": {
                Map<String, Object> data = node.getData();
                String defaultAction = data.get("defaultAction") != null ? data.get("defaultAction").toString() : "PASS";
                String defaultReason = data.get("defaultReason") != null ? data.get("defaultReason").toString() : "默认决策";
                return DecisionResponse.builder()
                        .decision(defaultAction)
                        .reason(defaultReason)
                        .executionTimeMs(0L)
                        .timeout(false)
                        .build();
            }
            default:
                return defaultResponse("未知节点类型: " + node.getType());
        }
    }

    /**
     * 评估条件节点
     */
    private DecisionResponse evaluateCondition(FlowNodeDef node, FlowGraph graph, Map<String, Object> features) {
        Map<String, Object> data = node.getData();
        String fieldName = data.get("fieldName") != null ? data.get("fieldName").toString() : "";
        String operator = data.get("operator") != null ? data.get("operator").toString() : "EQ";
        Object threshold = data.get("threshold");

        Object fieldValue = features.get(fieldName);
        boolean conditionMet = evaluateOperator(fieldValue, operator, threshold);

        FlowNodeDef next = getNextNode(node.getId(), graph, conditionMet);
        return next != null ? traverseNode(next, graph, features) : defaultResponse("条件分支无后续节点");
    }

    /**
     * 评估规则集节点
     */
    @SuppressWarnings("unchecked")
    private DecisionResponse evaluateRuleSet(FlowNodeDef node, FlowGraph graph, Map<String, Object> features) {
        Map<String, Object> data = node.getData();
        String logic = data.get("logic") != null ? data.get("logic").toString() : "AND";

        List<String> ruleKeys = data.get("ruleKeys") instanceof List
                ? ((List<?>) data.get("ruleKeys")).stream().map(Object::toString).collect(Collectors.toList())
                : List.of();

        if (ruleKeys.isEmpty()) {
            FlowNodeDef next = getNextNode(node.getId(), graph, false);
            return next != null ? traverseNode(next, graph, features) : defaultResponse("规则集无引用规则");
        }

        boolean allPass;
        if ("AND".equals(logic)) {
            allPass = ruleKeys.stream().allMatch(key -> executeRule(key, features));
        } else {
            allPass = ruleKeys.stream().anyMatch(key -> executeRule(key, features));
        }

        FlowNodeDef next = getNextNode(node.getId(), graph, allPass);
        return next != null ? traverseNode(next, graph, features) : defaultResponse("规则集分支无后续节点");
    }

    /**
     * 执行单个规则
     */
    private boolean executeRule(String ruleKey, Map<String, Object> features) {
        try {
            Rule rule = ruleCacheService.getEnabledRule(ruleKey);
            if (rule == null || rule.getStatus() != RuleStatus.ACTIVE) {
                return false;
            }
            Object result = scriptEngine.executeScript(ruleKey, rule.getGroovyScript(), features);
            if (result instanceof Boolean) return (Boolean) result;
            if (result instanceof Map) {
                Object decision = ((Map<?, ?>) result).get("decision");
                return decision != null && "PASS".equals(decision.toString());
            }
            return "PASS".equals(String.valueOf(result));
        } catch (Exception e) {
            log.warn("规则集引用规则执行失败: ruleKey={}", ruleKey, e);
            return false;
        }
    }

    /**
     * 评估条件运算符
     */
    private boolean evaluateOperator(Object fieldValue, String operator, Object threshold) {
        if (fieldValue == null) return false;
        try {
            double fv = Double.parseDouble(fieldValue.toString());
            double tv = Double.parseDouble(threshold.toString());
            switch (operator) {
                case "GT": return fv > tv;
                case "GE": return fv >= tv;
                case "LT": return fv < tv;
                case "LE": return fv <= tv;
                case "EQ": return fv == tv;
                case "NE": return fv != tv;
                default: return false;
            }
        } catch (NumberFormatException e) {
            String fv = fieldValue.toString();
            String tv = threshold.toString();
            switch (operator) {
                case "EQ": return fv.equals(tv);
                case "NE": return !fv.equals(tv);
                case "CONTAINS": return fv.contains(tv);
                case "NOT_CONTAINS": return !fv.contains(tv);
                default: return false;
            }
        }
    }

    /**
     * 获取下一个节点
     * 根据 sourceId 和条件结果匹配边
     */
    private FlowNodeDef getNextNode(String sourceId, FlowGraph graph, Boolean conditionMet) {
        for (FlowEdgeDef edge : graph.getEdges()) {
            if (edge.getSource().equals(sourceId)) {
                if (conditionMet == null) {
                    return findNode(edge.getTarget(), graph);
                }
                String handle = edge.getSourceHandle();
                if (conditionMet && "true".equals(handle)) {
                    return findNode(edge.getTarget(), graph);
                }
                if (!conditionMet && "false".equals(handle)) {
                    return findNode(edge.getTarget(), graph);
                }
            }
        }
        // Fallback: first outgoing edge
        for (FlowEdgeDef edge : graph.getEdges()) {
            if (edge.getSource().equals(sourceId)) {
                return findNode(edge.getTarget(), graph);
            }
        }
        return null;
    }

    /**
     * 根据 nodeId 查找节点
     */
    private FlowNodeDef findNode(String nodeId, FlowGraph graph) {
        return graph.getNodes().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 构建默认拒绝响应
     */
    private DecisionResponse defaultResponse(String reason) {
        return DecisionResponse.builder()
                .decision("REJECT")
                .reason(reason)
                .executionTimeMs(0L)
                .timeout(false)
                .build();
    }
}
