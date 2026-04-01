package com.example.ruleengine.model.flow;

import java.util.Map;

/**
 * 流程图边定义
 */
public class FlowEdgeDef {
    private String id;
    private String source;
    private String target;
    private String sourceHandle;
    private Map<String, Object> data;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getSourceHandle() { return sourceHandle; }
    public void setSourceHandle(String sourceHandle) { this.sourceHandle = sourceHandle; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
