package com.example.ruleengine.model.flow;

import java.util.List;

/**
 * 流程图定义模型
 * 对应前端 React Flow 导出的 JSON 结构
 */
public class FlowGraph {
    private List<FlowNodeDef> nodes;
    private List<FlowEdgeDef> edges;

    public List<FlowNodeDef> getNodes() { return nodes; }
    public void setNodes(List<FlowNodeDef> nodes) { this.nodes = nodes; }
    public List<FlowEdgeDef> getEdges() { return edges; }
    public void setEdges(List<FlowEdgeDef> edges) { this.edges = edges; }
}
