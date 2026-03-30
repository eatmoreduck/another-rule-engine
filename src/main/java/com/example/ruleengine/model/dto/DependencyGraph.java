package com.example.ruleengine.model.dto;

import java.util.List;
import java.util.Map;

/**
 * 规则依赖关系图数据
 * MON-04: 分析规则之间的特征依赖和执行顺序依赖
 */
public class DependencyGraph {

    private List<DependencyNode> nodes;
    private List<DependencyEdge> edges;
    private List<String> sharedFeatures;

    public DependencyGraph() {}

    public List<DependencyNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<DependencyNode> nodes) {
        this.nodes = nodes;
    }

    public List<DependencyEdge> getEdges() {
        return edges;
    }

    public void setEdges(List<DependencyEdge> edges) {
        this.edges = edges;
    }

    public List<String> getSharedFeatures() {
        return sharedFeatures;
    }

    public void setSharedFeatures(List<String> sharedFeatures) {
        this.sharedFeatures = sharedFeatures;
    }

    /**
     * 依赖关系节点
     */
    public static class DependencyNode {
        private String ruleKey;
        private String ruleName;
        private List<String> features;
        private long executionCount;

        public DependencyNode() {}

        public DependencyNode(String ruleKey, String ruleName,
                              List<String> features, long executionCount) {
            this.ruleKey = ruleKey;
            this.ruleName = ruleName;
            this.features = features;
            this.executionCount = executionCount;
        }

        public String getRuleKey() {
            return ruleKey;
        }

        public void setRuleKey(String ruleKey) {
            this.ruleKey = ruleKey;
        }

        public String getRuleName() {
            return ruleName;
        }

        public void setRuleName(String ruleName) {
            this.ruleName = ruleName;
        }

        public List<String> getFeatures() {
            return features;
        }

        public void setFeatures(List<String> features) {
            this.features = features;
        }

        public long getExecutionCount() {
            return executionCount;
        }

        public void setExecutionCount(long executionCount) {
            this.executionCount = executionCount;
        }
    }

    /**
     * 依赖关系边
     */
    public static class DependencyEdge {
        private String source;
        private String target;
        private String dependencyType;
        private List<String> sharedFeatureList;

        public DependencyEdge() {}

        public DependencyEdge(String source, String target, String dependencyType,
                              List<String> sharedFeatureList) {
            this.source = source;
            this.target = target;
            this.dependencyType = dependencyType;
            this.sharedFeatureList = sharedFeatureList;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public String getDependencyType() {
            return dependencyType;
        }

        public void setDependencyType(String dependencyType) {
            this.dependencyType = dependencyType;
        }

        public List<String> getSharedFeatureList() {
            return sharedFeatureList;
        }

        public void setSharedFeatureList(List<String> sharedFeatureList) {
            this.sharedFeatureList = sharedFeatureList;
        }
    }
}
