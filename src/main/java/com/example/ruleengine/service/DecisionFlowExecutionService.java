package com.example.ruleengine.service;

import com.example.ruleengine.cache.DecisionFlowCacheService;
import com.example.ruleengine.cache.RuleCacheService;
import com.example.ruleengine.domain.DecisionFlow;
import com.example.ruleengine.domain.DecisionFlowVersion;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.engine.GroovyScriptEngine;
import com.example.ruleengine.metrics.RuleExecutionMetrics;
import com.example.ruleengine.model.DecisionResponse;
import com.example.ruleengine.model.flow.FlowEdgeDef;
import com.example.ruleengine.model.flow.FlowGraph;
import com.example.ruleengine.model.flow.FlowNodeDef;
import com.example.ruleengine.repository.DecisionFlowVersionRepository;
import com.example.ruleengine.service.executionlog.ExecutionLogService;
import com.example.ruleengine.service.grayscale.CanaryExecutionLogService;
import com.example.ruleengine.service.grayscale.GrayscaleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final NameListService nameListService;
    private final GrayscaleService grayscaleService;
    private final DecisionFlowVersionRepository decisionFlowVersionRepository;
    private final CanaryExecutionLogService canaryExecutionLogService;
    private final RuleExecutionMetrics ruleExecutionMetrics;
    private final ExecutionLogService executionLogService;

    /**
     * 执行决策流
     *
     * @param flowKey  决策流Key
     * @param features 特征数据
     * @return 决策响应
     */
    public DecisionResponse executeFlow(String flowKey, Map<String, Object> features) {
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().replace("-", "");

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

            // 灰度分流：判断是否命中灰度版本
            String graphJson;
            int canaryVersion = resolveFlowVersion(flowKey, features);
            boolean isCanary = canaryVersion > 0;
            int versionUsed;

            if (isCanary) {
                graphJson = loadFlowVersionGraph(flowKey, canaryVersion);
                if (graphJson == null) {
                    log.warn("灰度版本 flowGraph 加载失败, fallback 到当前版本: flowKey={}, canaryVersion={}",
                            flowKey, canaryVersion);
                    graphJson = flow.getFlowGraph();
                    versionUsed = flow.getVersion();
                    isCanary = false;
                } else {
                    log.debug("决策流灰度命中: flowKey={}, canaryVersion={}", flowKey, canaryVersion);
                    versionUsed = canaryVersion;
                }
            } else {
                graphJson = flow.getFlowGraph();
                versionUsed = flow.getVersion();
            }

            FlowGraph graph = objectMapper.readValue(graphJson, FlowGraph.class);
            FlowNodeDef startNode = graph.getNodes().stream()
                    .filter(n -> "start".equals(n.getType()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("决策流没有开始节点"));

            DecisionResponse response = traverseNode(flowKey, startNode, graph, features);
            long execTime = System.currentTimeMillis() - startTime;
            response.setExecutionTimeMs(execTime);

            // 异步记录灰度执行日志
            int finalVersionUsed = versionUsed;
            boolean finalIsCanary = isCanary;
            canaryExecutionLogService.asyncLog(
                    traceId, "DECISION_FLOW", flowKey,
                    finalVersionUsed, finalIsCanary,
                    features, response.getDecision(), execTime);

            // 记录 Micrometer 指标（监控仪表盘数据来源）
            ruleExecutionMetrics.recordExecution(flowKey, response.getDecision(), execTime);

            // 异步记录执行日志（最近执行日志数据来源）
            executionLogService.logSuccess(
                    flowKey, versionUsed, features,
                    response.getDecision(), response.getReason(), execTime);

            return response;

        } catch (Exception e) {
            log.error("决策流执行失败: flowKey={}", flowKey, e);
            long execTime = System.currentTimeMillis() - startTime;

            // 异步记录灰度执行错误日志
            canaryExecutionLogService.asyncLogError(
                    traceId, "DECISION_FLOW", flowKey,
                    0, false, features,
                    e.getMessage(), execTime);

            // 记录 Micrometer 错误指标
            ruleExecutionMetrics.recordError(flowKey, e);

            // 异步记录执行错误日志
            executionLogService.logError(
                    flowKey, 0, features, execTime, e.getMessage());

            return DecisionResponse.builder()
                    .decision("REJECT")
                    .reason("决策流执行失败: " + e.getMessage())
                    .executionTimeMs(execTime)
                    .timeout(false)
                    .build();
        }
    }

    /**
     * 解析决策流灰度版本号
     * 异常时返回 0（不命中），确保不阻塞主流程
     */
    private int resolveFlowVersion(String flowKey, Map<String, Object> features) {
        try {
            return grayscaleService.resolveGrayscaleVersionForFlow(flowKey, features)
                    .orElse(0);
        } catch (Exception e) {
            log.error("决策流灰度版本解析异常, fallback 到当前版本: flowKey={}", flowKey, e);
            return 0;
        }
    }

    /**
     * 从决策流版本历史加载指定版本的 flowGraph
     */
    private String loadFlowVersionGraph(String flowKey, int version) {
        try {
            return decisionFlowVersionRepository.findByFlowKeyAndVersion(flowKey, version)
                    .map(DecisionFlowVersion::getFlowGraph)
                    .orElse(null);
        } catch (Exception e) {
            log.error("加载决策流版本 flowGraph 失败: flowKey={}, version={}", flowKey, version, e);
            return null;
        }
    }

    /**
     * 递归遍历流程节点
     */
    private DecisionResponse traverseNode(String flowKey, FlowNodeDef node, FlowGraph graph, Map<String, Object> features) {
        switch (node.getType()) {
            case "start": {
                FlowNodeDef next = getNextNode(node.getId(), graph, null);
                return next != null ? traverseNode(flowKey, next, graph, features) : defaultResponse("无后续节点");
            }
            case "condition": {
                return evaluateCondition(flowKey, node, graph, features);
            }
            case "ruleset": {
                return evaluateRuleSet(flowKey, node, graph, features);
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
            case "blacklist": {
                return evaluateBlacklist(flowKey, node, graph, features);
            }
            case "whitelist": {
                return evaluateWhitelist(flowKey, node, graph, features);
            }
            case "merge": {
                FlowNodeDef next = getNextNode(node.getId(), graph, null);
                return next != null ? traverseNode(flowKey, next, graph, features) : defaultResponse("合并节点无后续节点");
            }
            default:
                return defaultResponse("未知节点类型: " + node.getType());
        }
    }

    /**
     * 评估黑名单节点
     */
    private DecisionResponse evaluateBlacklist(String flowKey, FlowNodeDef node, FlowGraph graph, Map<String, Object> features) {
        Map<String, Object> data = node.getData();
        String keyType = data.get("keyType") != null ? data.get("keyType").toString() : "";
        // 支持节点配置 listKey： 精确匹配； 否则回退到 GLOBAL
        String listKey = data.get("listKey") != null ? data.get("listKey").toString() : "";
        Object featureValue = features.get(keyType);

        if (featureValue == null || featureValue.toString().isEmpty()) {
            FlowNodeDef next = getNextNode(node.getId(), graph, null);
            return next != null ? traverseNode(flowKey, next, graph, features) : defaultResponse("黑名单节点无后续节点");
        }

        String keyValue = featureValue.toString();
        boolean found = nameListService.existsInList(listKey, "BLACK", keyType, keyValue);
        if (!found && !"GLOBAL".equals(listKey)) {
            found = nameListService.existsInList("GLOBAL", "BLACK", keyType, keyValue);
        }

        if (found) {
            return DecisionResponse.builder()
                    .decision("REJECT")
                    .reason("命中黑名单: " + keyType + "=" + keyValue)
                    .build();
        }

        // 未命中，通过
        FlowNodeDef next = getNextNode(node.getId(), graph, null);
        return next != null ? traverseNode(flowKey, next, graph, features) : defaultResponse("黑名单节点无后续节点");
    }

    /**
     * 评估白名单节点
     */
    private DecisionResponse evaluateWhitelist(String flowKey, FlowNodeDef node, FlowGraph graph, Map<String, Object> features) {
        Map<String, Object> data = node.getData();
        String keyType = data.get("keyType") != null ? data.get("keyType").toString() : "";
        String listKey = data.get("listKey") != null ? data.get("listKey").toString() : "";
        Object featureValue = features.get(keyType);

        if (featureValue == null || featureValue.toString().isEmpty()) {
            return DecisionResponse.builder()
                    .decision("REJECT")
                    .reason("白名单校验失败: 缺少特征值 " + keyType)
                    .build();
        }

        String keyValue = featureValue.toString();
        boolean found = nameListService.existsInList(listKey, "WHITE", keyType, keyValue);
        if (!found && !"GLOBAL".equals(listKey)) {
            found = nameListService.existsInList("GLOBAL", "WHITE", keyType, keyValue);
        }

        if (found) {
            // 在白名单中，通过
            FlowNodeDef next = getNextNode(node.getId(), graph, null);
            return next != null ? traverseNode(flowKey, next, graph, features) : defaultResponse("白名单节点无后续节点");
        }

        // 不在白名单中，拒绝
        return DecisionResponse.builder()
                .decision("REJECT")
                .reason("未在白名单中: " + keyType + "=" + keyValue)
                .build();
    }

    /**
     * 评估条件节点
     */
    private DecisionResponse evaluateCondition(String flowKey, FlowNodeDef node, FlowGraph graph, Map<String, Object> features) {
        Map<String, Object> data = node.getData();
        String fieldName = data.get("fieldName") != null ? data.get("fieldName").toString() : "";
        String operator = data.get("operator") != null ? data.get("operator").toString() : "EQ";
        Object threshold = data.get("threshold");

        Object fieldValue = features.get(fieldName);
        boolean conditionMet = evaluateOperator(fieldValue, operator, threshold);

        FlowNodeDef next = getNextNode(node.getId(), graph, conditionMet);
        return next != null ? traverseNode(flowKey, next, graph, features) : defaultResponse("条件分支无后续节点");
    }

    /**
     * 评估规则集节点（拒绝优先模式）
     * 遍历引用规则，任一返回 REJECT 则立即返回拒绝决策（一票否决）
     * 无拒绝时走"通过"分支继续流程
     */
    @SuppressWarnings("unchecked")
    private DecisionResponse evaluateRuleSet(String flowKey, FlowNodeDef node, FlowGraph graph, Map<String, Object> features) {
        Map<String, Object> data = node.getData();

        List<String> ruleKeys = data.get("ruleKeys") instanceof List
                ? ((List<?>) data.get("ruleKeys")).stream().map(Object::toString).collect(Collectors.toList())
                : List.of();

        if (ruleKeys.isEmpty()) {
            // 无引用规则，走通过分支
            FlowNodeDef next = getNextNode(node.getId(), graph, null);
            return next != null ? traverseNode(flowKey, next, graph, features) : defaultResponse("规则集无引用规则");
        }

        // 拒绝优先模式：逐个执行，任一 REJECT 立即短路返回
        for (String ruleKey : ruleKeys) {
            RuleExecResult execResult = executeRuleWithDecision(ruleKey, features);
            if (execResult.rejected) {
                log.info("规则集拒绝优先触发: node={}, ruleKey={}, reason={}", node.getId(), ruleKey, execResult.reason);
                return DecisionResponse.builder()
                        .decision("REJECT")
                        .reason(execResult.reason != null ? execResult.reason : "规则集命中拒绝规则: " + ruleKey)
                        .build();
            }
        }

        // 全部规则无拒绝，走通过分支继续流程
        FlowNodeDef next = getNextNode(node.getId(), graph, null);
        return next != null ? traverseNode(flowKey, next, graph, features) : defaultResponse("规则集通过分支无后续节点");
    }

    /** 规则执行结果（含决策信息） */
    private record RuleExecResult(boolean rejected, String reason) {}

    /**
     * 执行单个规则并返回详细决策结果
     */
    private RuleExecResult executeRuleWithDecision(String ruleKey, Map<String, Object> features) {
        try {
            Rule rule = ruleCacheService.getEnabledRule(ruleKey);
            if (rule == null || !rule.getEnabled() || rule.getDeleted()) {
                return new RuleExecResult(false, null);
            }
            Object result = scriptEngine.executeScript(ruleKey, rule.getGroovyScript(), features);
            if (result instanceof Map) {
                Object decision = ((Map<?, ?>) result).get("decision");
                Object reason = ((Map<?, ?>) result).get("reason");
                if (decision != null && "REJECT".equals(decision.toString())) {
                    return new RuleExecResult(true, reason != null ? reason.toString() : null);
                }
                return new RuleExecResult(false, reason != null ? reason.toString() : null);
            }
            if (result instanceof Boolean) {
                return new RuleExecResult(!(Boolean) result, null);
            }
            String str = String.valueOf(result);
            return new RuleExecResult("REJECT".equals(str), null);
        } catch (Exception e) {
            log.warn("规则集引用规则执行失败: ruleKey={}", ruleKey, e);
            return new RuleExecResult(false, null);
        }
    }

    /**
     * 执行单个规则
     */
    private boolean executeRule(String ruleKey, Map<String, Object> features) {
        try {
            Rule rule = ruleCacheService.getEnabledRule(ruleKey);
            if (rule == null || !rule.getEnabled() || rule.getDeleted()) {
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
                    // null = 无条件分支，优先匹配 "pass" handle，否则走第一条出边
                    String handle = edge.getSourceHandle();
                    if ("pass".equals(handle) || handle == null) {
                        return findNode(edge.getTarget(), graph);
                    }
                    continue;
                }
                String handle = edge.getSourceHandle();
                if (conditionMet && ("true".equals(handle) || "pass".equals(handle))) {
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
