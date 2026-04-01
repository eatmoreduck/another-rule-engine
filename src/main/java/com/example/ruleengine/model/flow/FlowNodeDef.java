package com.example.ruleengine.model.flow;

import java.util.Map;

/**
 * 流程图节点定义
 */
public class FlowNodeDef {
    private String id;
    private String type;
    private Map<String, Object> data;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
