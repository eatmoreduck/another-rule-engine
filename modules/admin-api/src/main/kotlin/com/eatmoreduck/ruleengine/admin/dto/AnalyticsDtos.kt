package com.eatmoreduck.ruleengine.admin.dto

/**
 * 规则效果分析数据（对应旧 RuleAnalytics：MON-03 命中率/拦截率/误判口径）。
 * 聚合口径与旧实现逐字段一致（比率不四舍五入，0-100 百分比）。
 */
data class RuleAnalyticsResponse(
    val ruleKey: String,
    val ruleName: String,
    val totalExecutions: Long,
    val hitCount: Long,
    val hitRate: Double,
    val rejectCount: Long,
    val rejectRate: Double,
    val passCount: Long,
    val passRate: Double,
    val errorCount: Long,
    val errorRate: Double,
    val avgExecutionTimeMs: Double,
    val maxExecutionTimeMs: Double,
    val p99ExecutionTimeMs: Double,
    val trendData: List<TrendDataPoint>,
) {
    /** 趋势数据点（对应旧 RuleAnalytics.TrendDataPoint） */
    data class TrendDataPoint(
        val date: String,
        val executions: Long,
        val hits: Long,
        val hitRate: Double,
        val avgExecutionTimeMs: Double,
    )
}

/**
 * 规则依赖关系图数据（对应旧 DependencyGraph：MON-04 特征依赖分析）。
 */
data class DependencyGraphResponse(
    val nodes: List<DependencyNode>,
    val edges: List<DependencyEdge>,
    val sharedFeatures: List<String>,
) {
    /** 依赖关系节点（对应旧 DependencyGraph.DependencyNode） */
    data class DependencyNode(
        val ruleKey: String,
        val ruleName: String,
        val features: List<String>,
        /** 旧实现固定 0（当时未从日志取数），保持一致 */
        val executionCount: Long,
    )

    /** 依赖关系边（对应旧 DependencyGraph.DependencyEdge） */
    data class DependencyEdge(
        val source: String,
        val target: String,
        /** 旧实现恒为 FEATURE_DEPENDENCY */
        val dependencyType: String,
        val sharedFeatureList: List<String>,
    )
}

/**
 * 规则冲突检测结果（对应旧 ConflictResult：TEST-03 条件冲突/决策冲突）。
 */
data class ConflictResultResponse(
    /** CONDITION_CONFLICT / DECISION_CONFLICT */
    val conflictType: String,
    val ruleKey1: String,
    val ruleName1: String,
    val ruleKey2: String,
    val ruleName2: String,
    val description: String,
    /** HIGH / MEDIUM */
    val severity: String,
)
