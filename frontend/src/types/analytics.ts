/** 测试执行结果 */
export interface TestResult {
  ruleKey: string;
  decision: string;
  reason: string;
  executionTimeMs: number;
  success: boolean;
  errorMessage?: string;
  matchedConditions: string[];
  executionContext?: Record<string, unknown>;
}

/** 批量测试请求 */
export interface BatchTestRequest {
  ruleKey: string;
  testDataList: Record<string, unknown>[];
}

/** 冲突检测结果 */
export interface ConflictResult {
  conflictType: string;
  ruleKey1: string;
  ruleName1: string;
  ruleKey2: string;
  ruleName2: string;
  description: string;
  severity: string;
}

/** 规则效果分析数据 */
export interface RuleAnalytics {
  ruleKey: string;
  ruleName: string;
  totalExecutions: number;
  hitCount: number;
  hitRate: number;
  rejectCount: number;
  rejectRate: number;
  passCount: number;
  passRate: number;
  errorCount: number;
  errorRate: number;
  avgExecutionTimeMs: number;
  maxExecutionTimeMs: number;
  p99ExecutionTimeMs: number;
  trendData: TrendDataPoint[];
}

/** 趋势数据点 */
export interface TrendDataPoint {
  date: string;
  executions: number;
  hits: number;
  hitRate: number;
  avgExecutionTimeMs: number;
}

/** 依赖关系图节点 */
export interface DependencyNode {
  ruleKey: string;
  ruleName: string;
  features: string[];
  executionCount: number;
}

/** 依赖关系图边 */
export interface DependencyEdge {
  source: string;
  target: string;
  dependencyType: string;
  sharedFeatureList: string[];
}

/** 依赖关系图 */
export interface DependencyGraph {
  nodes: DependencyNode[];
  edges: DependencyEdge[];
  sharedFeatures: string[];
}
