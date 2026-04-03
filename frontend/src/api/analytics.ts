import apiClient from './client';
import type {
  TestResult,
  ConflictResult,
  RuleAnalytics,
  DependencyGraph,
} from '../types/analytics';

/** 用模拟数据测试规则 */
export async function executeTest(
  ruleKey: string,
  testData: Record<string, unknown>,
): Promise<TestResult> {
  const response = await apiClient.post<TestResult>(
    `/api/v1/test/rules/${ruleKey}/execute`,
    testData,
  );
  return response.data;
}

/** 全量冲突检测 */
export async function detectAllConflicts(): Promise<ConflictResult[]> {
  const response = await apiClient.post<ConflictResult[]>(
    '/api/v1/conflicts/detect',
  );
  return response.data;
}

/** 单规则冲突检测 */
export async function detectConflictsForRule(
  ruleKey: string,
): Promise<ConflictResult[]> {
  const response = await apiClient.get<ConflictResult[]>(
    `/api/v1/conflicts/rule/${ruleKey}`,
  );
  return response.data;
}

/** 获取规则效果分析 */
export async function getRuleAnalytics(
  ruleKey: string,
  startDate?: string,
  endDate?: string,
): Promise<RuleAnalytics> {
  const response = await apiClient.get<RuleAnalytics>(
    `/api/v1/analytics/rules/${ruleKey}`,
    { params: { startDate, endDate } },
  );
  return response.data;
}

/** 获取全局分析概览 */
export async function getAnalyticsOverview(
  startDate?: string,
  endDate?: string,
): Promise<RuleAnalytics[]> {
  const response = await apiClient.get<RuleAnalytics[]>(
    '/api/v1/analytics/overview',
    { params: { startDate, endDate } },
  );
  return response.data;
}

/** 获取依赖关系图 */
export async function getDependencyGraph(): Promise<DependencyGraph> {
  const response = await apiClient.get<DependencyGraph>(
    '/api/v1/analytics/dependencies',
  );
  return response.data;
}

/** 获取单规则依赖关系 */
export async function getRuleDependencies(
  ruleKey: string,
): Promise<DependencyGraph> {
  const response = await apiClient.get<DependencyGraph>(
    `/api/v1/analytics/dependencies/${ruleKey}`,
  );
  return response.data;
}
