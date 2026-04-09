package com.example.ruleengine.service;

import com.example.ruleengine.cache.DecisionFlowCacheService;
import com.example.ruleengine.cache.RuleCacheService;
import com.example.ruleengine.domain.DecisionFlow;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DecisionFlowExecutionService 单元测试
 *
 * 测试覆盖:
 * 1. 基础流程执行 (开始→结束)
 * 2. 条件节点 (true/false 分支)
 * 3. 规则集节点 (拒绝优先模式)
 * 4. 黑名单节点
 * 5. 白名单节点
 * 6. 动作节点
 * 7. 合并节点
 * 8. 异常场景 (流程不存在、无开始节点)
 * 9. 运算符评估
 */
@DisplayName("DecisionFlowExecutionService 测试")
@ExtendWith(MockitoExtension.class)
class DecisionFlowExecutionServiceTest {

    @Mock
    private DecisionFlowCacheService flowCacheService;
    @Mock
    private RuleCacheService ruleCacheService;
    @Mock
    private GroovyScriptEngine scriptEngine;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private NameListService nameListService;
    @Mock
    private GrayscaleService grayscaleService;
    @Mock
    private DecisionFlowVersionRepository decisionFlowVersionRepository;
    @Mock
    private CanaryExecutionLogService canaryExecutionLogService;
    @Mock
    private RuleExecutionMetrics ruleExecutionMetrics;
    @Mock
    private ExecutionLogService executionLogService;

    private DecisionFlowExecutionService service;

    private ObjectMapper realObjectMapper;

    @BeforeEach
    void setUp() {
        // 使用真实 ObjectMapper 解析 JSON，mock 版本无法做反序列化
        realObjectMapper = new ObjectMapper();
        // 创建 service 实例，注入 mock 和真实 ObjectMapper
        service = new DecisionFlowExecutionService(
                flowCacheService, ruleCacheService, scriptEngine,
                realObjectMapper, nameListService, grayscaleService,
                decisionFlowVersionRepository, canaryExecutionLogService,
                ruleExecutionMetrics, executionLogService);
    }

    // ========== 辅助方法 ==========

    private FlowNodeDef node(String id, String type) {
        return node(id, type, null);
    }

    private FlowNodeDef node(String id, String type, Map<String, Object> data) {
        FlowNodeDef n = new FlowNodeDef();
        n.setId(id);
        n.setType(type);
        n.setData(data);
        return n;
    }

    private FlowEdgeDef edge(String source, String target, String sourceHandle) {
        FlowEdgeDef e = new FlowEdgeDef();
        e.setId("e-" + source + "-" + target);
        e.setSource(source);
        e.setTarget(target);
        e.setSourceHandle(sourceHandle);
        return e;
    }

    private FlowGraph graph(FlowNodeDef[] nodes, FlowEdgeDef[] edges) {
        FlowGraph g = new FlowGraph();
        g.setNodes(Arrays.asList(nodes));
        g.setEdges(Arrays.asList(edges));
        return g;
    }

    private DecisionFlow mockFlow(FlowGraph graph) throws Exception {
        DecisionFlow flow = new DecisionFlow();
        flow.setFlowKey("test-flow");
        flow.setVersion(1);
        flow.setEnabled(true);
        flow.setFlowGraph(realObjectMapper.writeValueAsString(graph));
        return flow;
    }

    /** 设置灰度返回不命中（默认） */
    private void noGrayscale() {
        when(grayscaleService.resolveGrayscaleVersionForFlow(anyString(), anyMap()))
                .thenReturn(Optional.empty());
    }

    // ========== 测试类 ==========

    @Nested
    @DisplayName("基础流程执行")
    class BasicFlowTests {

        @Test
        @DisplayName("开始→结束: 默认 PASS")
        void startToEnd_defaultPass() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("end", "end", Map.of("defaultAction", "PASS", "defaultReason", "通过"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "end", "pass")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();

            DecisionResponse response = service.executeFlow("test-flow", Map.of());

            assertEquals("PASS", response.getDecision());
            assertEquals("通过", response.getReason());
            assertFalse(response.isTimeout());
            assertTrue(response.getExecutionTimeMs() >= 0);
        }

        @Test
        @DisplayName("开始→结束: 默认 REJECT")
        void startToEnd_defaultReject() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("end", "end", Map.of("defaultAction", "REJECT", "defaultReason", "拒绝"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "end", "pass")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();

            DecisionResponse response = service.executeFlow("test-flow", Map.of());

            assertEquals("REJECT", response.getDecision());
            assertEquals("拒绝", response.getReason());
        }

        @Test
        @DisplayName("流程不存在: 返回 REJECT")
        void flowNotFound() {
            when(flowCacheService.getEnabledFlow("missing")).thenReturn(null);

            DecisionResponse response = service.executeFlow("missing", Map.of());

            assertEquals("REJECT", response.getDecision());
            assertTrue(response.getReason().contains("不存在"));
        }
    }

    @Nested
    @DisplayName("条件节点")
    class ConditionNodeTests {

        @Test
        @DisplayName("条件成立: 走 true 分支")
        void conditionTrue() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("cond", "condition", Map.of("fieldName", "amount", "operator", "GT", "threshold", 100)),
                            node("reject", "end", Map.of("defaultAction", "REJECT", "defaultReason", "金额过大")),
                            node("pass", "end", Map.of("defaultAction", "PASS", "defaultReason", "金额正常"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "cond", "pass"),
                            edge("cond", "reject", "true"),
                            edge("cond", "pass", "false")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();

            // amount=200 > 100 → true 分支 → REJECT
            DecisionResponse response = service.executeFlow("test-flow", Map.of("amount", 200));
            assertEquals("REJECT", response.getDecision());
        }

        @Test
        @DisplayName("条件不成立: 走 false 分支")
        void conditionFalse() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("cond", "condition", Map.of("fieldName", "amount", "operator", "GT", "threshold", 100)),
                            node("reject", "end", Map.of("defaultAction", "REJECT", "defaultReason", "金额过大")),
                            node("pass", "end", Map.of("defaultAction", "PASS", "defaultReason", "金额正常"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "cond", "pass"),
                            edge("cond", "reject", "true"),
                            edge("cond", "pass", "false")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();

            // amount=50 > 100 → false → PASS
            DecisionResponse response = service.executeFlow("test-flow", Map.of("amount", 50));
            assertEquals("PASS", response.getDecision());
        }

        @Test
        @DisplayName("条件字段不存在: 走 false 分支")
        void conditionFieldMissing() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("cond", "condition", Map.of("fieldName", "amount", "operator", "GT", "threshold", 100)),
                            node("reject", "end", Map.of("defaultAction", "REJECT", "defaultReason", "拒绝")),
                            node("pass", "end", Map.of("defaultAction", "PASS", "defaultReason", "通过"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "cond", "pass"),
                            edge("cond", "reject", "true"),
                            edge("cond", "pass", "false")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();

            // features 中没有 amount → fieldValue=null → false
            DecisionResponse response = service.executeFlow("test-flow", Map.of());
            assertEquals("PASS", response.getDecision());
        }
    }

    @Nested
    @DisplayName("动作节点")
    class ActionNodeTests {

        @Test
        @DisplayName("动作节点: REJECT 决策")
        void actionReject() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("action", "action", Map.of("action", "REJECT", "reason", "触发风控规则"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "action", "pass")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();

            DecisionResponse response = service.executeFlow("test-flow", Map.of());
            assertEquals("REJECT", response.getDecision());
            assertEquals("触发风控规则", response.getReason());
        }

        @Test
        @DisplayName("动作节点: PASS 决策")
        void actionPass() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("action", "action", Map.of("action", "PASS", "reason", "验证通过"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "action", "pass")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();

            DecisionResponse response = service.executeFlow("test-flow", Map.of());
            assertEquals("PASS", response.getDecision());
        }
    }

    @Nested
    @DisplayName("规则集节点")
    class RuleSetNodeTests {

        @Test
        @DisplayName("规则集: 引用规则返回 REJECT → 短路拒绝")
        void ruleSetReject() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("rs", "ruleset", Map.of("ruleKeys", List.of("rule-1"))),
                            node("pass", "end", Map.of("defaultAction", "PASS", "defaultReason", "通过"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "rs", "pass"),
                            edge("rs", "pass", "pass")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();

            Rule rule = new Rule();
            rule.setRuleKey("rule-1");
            rule.setEnabled(true);
            rule.setDeleted(false);
            rule.setGroovyScript("return [decision: 'REJECT', reason: '命中规则']");
            when(ruleCacheService.getEnabledRule("rule-1")).thenReturn(rule);
            when(scriptEngine.executeScript(eq("rule-1"), anyString(), anyMap()))
                    .thenReturn(Map.of("decision", "REJECT", "reason", "命中规则"));

            DecisionResponse response = service.executeFlow("test-flow", Map.of());
            assertEquals("REJECT", response.getDecision());
        }

        @Test
        @DisplayName("规则集: 所有规则 PASS → 走通过分支")
        void ruleSetAllPass() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("rs", "ruleset", Map.of("ruleKeys", List.of("rule-1"))),
                            node("pass", "end", Map.of("defaultAction", "PASS", "defaultReason", "通过"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "rs", "pass"),
                            edge("rs", "pass", "pass")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();

            Rule rule = new Rule();
            rule.setRuleKey("rule-1");
            rule.setEnabled(true);
            rule.setDeleted(false);
            rule.setGroovyScript("return [decision: 'PASS']");
            when(ruleCacheService.getEnabledRule("rule-1")).thenReturn(rule);
            when(scriptEngine.executeScript(eq("rule-1"), anyString(), anyMap()))
                    .thenReturn(Map.of("decision", "PASS"));

            DecisionResponse response = service.executeFlow("test-flow", Map.of());
            assertEquals("PASS", response.getDecision());
        }

        @Test
        @DisplayName("规则集: 空规则列表 → 走通过分支")
        void ruleSetEmpty() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("rs", "ruleset", Map.of()),
                            node("pass", "end", Map.of("defaultAction", "PASS", "defaultReason", "通过"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "rs", "pass"),
                            edge("rs", "pass", "pass")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();

            DecisionResponse response = service.executeFlow("test-flow", Map.of());
            assertEquals("PASS", response.getDecision());
        }
    }

    @Nested
    @DisplayName("黑名单节点")
    class BlacklistNodeTests {

        @Test
        @DisplayName("命中黑名单: 返回 REJECT")
        void blacklistHit() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("bl", "blacklist", Map.of("keyType", "userId", "listKey", "user-blacklist")),
                            node("pass", "end", Map.of("defaultAction", "PASS", "defaultReason", "通过"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "bl", "pass"),
                            edge("bl", "pass", "pass")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();
            when(nameListService.existsInList("user-blacklist", "BLACK", "userId", "user-123")).thenReturn(true);

            DecisionResponse response = service.executeFlow("test-flow", Map.of("userId", "user-123"));
            assertEquals("REJECT", response.getDecision());
            assertTrue(response.getReason().contains("命中黑名单"));
        }

        @Test
        @DisplayName("未命中黑名单: 继续流程")
        void blacklistMiss() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("bl", "blacklist", Map.of("keyType", "userId", "listKey", "user-blacklist")),
                            node("pass", "end", Map.of("defaultAction", "PASS", "defaultReason", "通过"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "bl", "pass"),
                            edge("bl", "pass", "pass")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();
            when(nameListService.existsInList("user-blacklist", "BLACK", "userId", "user-456")).thenReturn(false);
            when(nameListService.existsInList("GLOBAL", "BLACK", "userId", "user-456")).thenReturn(false);

            DecisionResponse response = service.executeFlow("test-flow", Map.of("userId", "user-456"));
            assertEquals("PASS", response.getDecision());
        }

        @Test
        @DisplayName("黑名单: 特征值为空 → 跳过检查继续流程")
        void blacklistMissingFeature() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("bl", "blacklist", Map.of("keyType", "userId")),
                            node("pass", "end", Map.of("defaultAction", "PASS", "defaultReason", "通过"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "bl", "pass"),
                            edge("bl", "pass", "pass")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();

            DecisionResponse response = service.executeFlow("test-flow", Map.of());
            assertEquals("PASS", response.getDecision());
        }
    }

    @Nested
    @DisplayName("白名单节点")
    class WhitelistNodeTests {

        @Test
        @DisplayName("在白名单中: 继续流程")
        void whitelistHit() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("wl", "whitelist", Map.of("keyType", "ip", "listKey", "ip-whitelist")),
                            node("pass", "end", Map.of("defaultAction", "PASS", "defaultReason", "通过"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "wl", "pass"),
                            edge("wl", "pass", "pass")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();
            when(nameListService.existsInList("ip-whitelist", "WHITE", "ip", "192.168.1.1")).thenReturn(true);

            DecisionResponse response = service.executeFlow("test-flow", Map.of("ip", "192.168.1.1"));
            assertEquals("PASS", response.getDecision());
        }

        @Test
        @DisplayName("不在白名单中: 返回 REJECT")
        void whitelistMiss() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("wl", "whitelist", Map.of("keyType", "ip", "listKey", "ip-whitelist"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "wl", "pass")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();
            when(nameListService.existsInList("ip-whitelist", "WHITE", "ip", "10.0.0.1")).thenReturn(false);
            when(nameListService.existsInList("GLOBAL", "WHITE", "ip", "10.0.0.1")).thenReturn(false);

            DecisionResponse response = service.executeFlow("test-flow", Map.of("ip", "10.0.0.1"));
            assertEquals("REJECT", response.getDecision());
            assertTrue(response.getReason().contains("未在白名单中"));
        }

        @Test
        @DisplayName("白名单: 特征值缺失 → REJECT")
        void whitelistMissingFeature() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("wl", "whitelist", Map.of("keyType", "ip"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "wl", "pass")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();

            DecisionResponse response = service.executeFlow("test-flow", Map.of());
            assertEquals("REJECT", response.getDecision());
            assertTrue(response.getReason().contains("缺少特征值"));
        }
    }

    @Nested
    @DisplayName("合并节点")
    class MergeNodeTests {

        @Test
        @DisplayName("合并节点: 透传到下一个节点")
        void mergePassesThrough() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("merge", "merge"),
                            node("end", "end", Map.of("defaultAction", "PASS", "defaultReason", "合并后通过"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "merge", "pass"),
                            edge("merge", "end", "pass")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();

            DecisionResponse response = service.executeFlow("test-flow", Map.of());
            assertEquals("PASS", response.getDecision());
        }
    }

    @Nested
    @DisplayName("复杂流程")
    class ComplexFlowTests {

        @Test
        @DisplayName("完整流程: 黑名单→条件→规则集→结束")
        void fullPipeline() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("bl", "blacklist", Map.of("keyType", "userId", "listKey", "bl-1")),
                            node("cond", "condition", Map.of("fieldName", "amount", "operator", "GT", "threshold", 500)),
                            node("rs", "ruleset", Map.of("ruleKeys", List.of("risk-rule"))),
                            node("end", "end", Map.of("defaultAction", "PASS", "defaultReason", "安全"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "bl", "pass"),
                            edge("bl", "cond", "pass"),
                            edge("cond", "rs", "true"),
                            edge("cond", "end", "false"),
                            edge("rs", "end", "pass")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();
            when(nameListService.existsInList("bl-1", "BLACK", "userId", "user-1")).thenReturn(false);
            when(nameListService.existsInList("GLOBAL", "BLACK", "userId", "user-1")).thenReturn(false);

            // amount=800 > 500 → true → ruleset → no rule rejects → PASS
            DecisionResponse response = service.executeFlow("test-flow",
                    Map.of("userId", "user-1", "amount", 800));

            // 规则集未命中任何规则 → 走 pass 分支 → PASS
            assertEquals("PASS", response.getDecision());
        }

        @Test
        @DisplayName("黑名单优先拦截: 不继续后续流程")
        void blacklistShortCircuit() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("bl", "blacklist", Map.of("keyType", "userId", "listKey", "bl-1")),
                            node("end", "end", Map.of("defaultAction", "PASS", "defaultReason", "通过"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "bl", "pass"),
                            edge("bl", "end", "pass")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();
            when(nameListService.existsInList("bl-1", "BLACK", "userId", "bad-user")).thenReturn(true);

            DecisionResponse response = service.executeFlow("test-flow",
                    Map.of("userId", "bad-user"));
            assertEquals("REJECT", response.getDecision());
            assertTrue(response.getReason().contains("命中黑名单"));
        }
    }

    @Nested
    @DisplayName("运算符评估")
    class OperatorTests {

        private DecisionFlowExecutionService directService;

        /**
         * 创建一个能直接测试 traverseNode 的 service（通过 executeFlow 间接调用）
         * 使用最简流程: start → condition → end
         */
        private DecisionResponse runConditionFlow(String fieldName, Object fieldValue,
                                                   String operator, Object threshold) throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("cond", "condition", Map.of("fieldName", fieldName, "operator", operator, "threshold", threshold)),
                            node("end-true", "end", Map.of("defaultAction", "REJECT", "defaultReason", "true")),
                            node("end-false", "end", Map.of("defaultAction", "PASS", "defaultReason", "false"))
                    },
                    new FlowEdgeDef[]{
                            edge("start", "cond", "pass"),
                            edge("cond", "end-true", "true"),
                            edge("cond", "end-false", "false")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();

            return service.executeFlow("test-flow", Map.of(fieldName, fieldValue));
        }

        @Test
        @DisplayName("GT: 200 > 100 → true")
        void gtTrue() throws Exception {
            assertEquals("REJECT", runConditionFlow("val", 200, "GT", 100).getDecision());
        }

        @Test
        @DisplayName("GT: 50 > 100 → false")
        void gtFalse() throws Exception {
            assertEquals("PASS", runConditionFlow("val", 50, "GT", 100).getDecision());
        }

        @Test
        @DisplayName("EQ: 100 == 100 → true")
        void eqTrue() throws Exception {
            assertEquals("REJECT", runConditionFlow("val", 100, "EQ", 100).getDecision());
        }

        @Test
        @DisplayName("NE: 100 != 200 → true")
        void neTrue() throws Exception {
            assertEquals("REJECT", runConditionFlow("val", 100, "NE", 200).getDecision());
        }

        @Test
        @DisplayName("LT: 50 < 100 → true")
        void ltTrue() throws Exception {
            assertEquals("REJECT", runConditionFlow("val", 50, "LT", 100).getDecision());
        }

        @Test
        @DisplayName("GE: 100 >= 100 → true")
        void geTrue() throws Exception {
            assertEquals("REJECT", runConditionFlow("val", 100, "GE", 100).getDecision());
        }

        @Test
        @DisplayName("LE: 100 <= 100 → true")
        void leTrue() throws Exception {
            assertEquals("REJECT", runConditionFlow("val", 100, "LE", 100).getDecision());
        }

        @Test
        @DisplayName("CONTAINS: 字符串包含")
        void containsOperator() throws Exception {
            assertEquals("REJECT", runConditionFlow("val", "hello world", "CONTAINS", "world").getDecision());
        }

        @Test
        @DisplayName("未知运算符: 返回 false")
        void unknownOperator() throws Exception {
            assertEquals("PASS", runConditionFlow("val", 100, "UNKNOWN", 100).getDecision());
        }
    }

    @Nested
    @DisplayName("灰度分流")
    class GrayscaleTests {

        @Test
        @DisplayName("灰度命中: 使用灰度版本 flowGraph")
        void grayscaleHit() throws Exception {
            // 当前版本: PASS
            FlowGraph currentGraph = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("end", "end", Map.of("defaultAction", "PASS", "defaultReason", "当前版本"))
                    },
                    new FlowEdgeDef[]{edge("start", "end", "pass")}
            );

            // 灰度版本: REJECT
            FlowGraph canaryGraph = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("end", "end", Map.of("defaultAction", "REJECT", "defaultReason", "灰度版本"))
                    },
                    new FlowEdgeDef[]{edge("start", "end", "pass")}
            );

            DecisionFlow flow = mockFlow(currentGraph);
            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(flow);
            when(grayscaleService.resolveGrayscaleVersionForFlow("test-flow", Map.of()))
                    .thenReturn(Optional.of(2));

            // mock 灰度版本的 flowGraph
            when(decisionFlowVersionRepository.findByFlowKeyAndVersion("test-flow", 2))
                    .thenReturn(Optional.of(createFlowVersion(canaryGraph)));

            DecisionResponse response = service.executeFlow("test-flow", Map.of());
            assertEquals("REJECT", response.getDecision());
            assertEquals("灰度版本", response.getReason());
        }

        @Test
        @DisplayName("灰度版本加载失败: fallback 到当前版本")
        void grayscaleFallback() throws Exception {
            FlowGraph currentGraph = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("end", "end", Map.of("defaultAction", "PASS", "defaultReason", "当前版本"))
                    },
                    new FlowEdgeDef[]{edge("start", "end", "pass")}
            );

            DecisionFlow flow = mockFlow(currentGraph);
            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(flow);
            when(grayscaleService.resolveGrayscaleVersionForFlow("test-flow", Map.of()))
                    .thenReturn(Optional.of(2));
            when(decisionFlowVersionRepository.findByFlowKeyAndVersion("test-flow", 2))
                    .thenReturn(Optional.empty());

            DecisionResponse response = service.executeFlow("test-flow", Map.of());
            assertEquals("PASS", response.getDecision());
            assertEquals("当前版本", response.getReason());
        }
    }

    @Nested
    @DisplayName("异常场景")
    class ErrorTests {

        @Test
        @DisplayName("无开始节点: 返回 REJECT 并包含错误信息")
        void noStartNode() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("end", "end", Map.of("defaultAction", "PASS", "defaultReason", "通过"))
                    },
                    new FlowEdgeDef[]{}
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();

            DecisionResponse response = service.executeFlow("test-flow", Map.of());
            assertEquals("REJECT", response.getDecision());
            assertTrue(response.getReason().contains("执行失败"));
        }

        @Test
        @DisplayName("flowGraph JSON 无效: 返回 REJECT")
        void invalidFlowGraph() {
            DecisionFlow flow = new DecisionFlow();
            flow.setFlowKey("test-flow");
            flow.setVersion(1);
            flow.setEnabled(true);
            flow.setFlowGraph("invalid-json{{{");

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(flow);
            noGrayscale();

            DecisionResponse response = service.executeFlow("test-flow", Map.of());
            assertEquals("REJECT", response.getDecision());
            assertTrue(response.getReason().contains("执行失败"));
        }

        @Test
        @DisplayName("未知节点类型: 返回 REJECT")
        void unknownNodeType() throws Exception {
            FlowGraph g = graph(
                    new FlowNodeDef[]{
                            node("start", "start"),
                            node("unknown", "custom_type")
                    },
                    new FlowEdgeDef[]{
                            edge("start", "unknown", "pass")
                    }
            );

            when(flowCacheService.getEnabledFlow("test-flow")).thenReturn(mockFlow(g));
            noGrayscale();

            DecisionResponse response = service.executeFlow("test-flow", Map.of());
            assertEquals("REJECT", response.getDecision());
            assertTrue(response.getReason().contains("未知节点类型"));
        }
    }

    // ========== 辅助 ==========

    private com.example.ruleengine.domain.DecisionFlowVersion createFlowVersion(FlowGraph graph) throws Exception {
        com.example.ruleengine.domain.DecisionFlowVersion version = new com.example.ruleengine.domain.DecisionFlowVersion();
        version.setFlowGraph(realObjectMapper.writeValueAsString(graph));
        return version;
    }
}
