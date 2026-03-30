import apiClient from './client';
import type {
  MetricsOverview,
  RuleMetrics,
  ExecutionLog,
  RuleMetricsQueryParams,
  RecentLogsQueryParams,
} from '../types/metrics';

/** 获取监控总览数据 */
export async function getMetricsOverview(): Promise<MetricsOverview> {
  const response = await apiClient.get<MetricsOverview>('/api/v1/metrics/overview');
  return response.data;
}

/** 获取单条规则的执行指标 */
export async function getRuleMetrics(ruleKey: string): Promise<RuleMetrics> {
  const response = await apiClient.get<RuleMetrics>(`/api/v1/metrics/rules/${ruleKey}`);
  return response.data;
}

/** 获取规则执行排行（按执行次数排序） */
export async function getRuleMetricsRanking(
  params?: RuleMetricsQueryParams,
): Promise<RuleMetrics[]> {
  const response = await apiClient.get<RuleMetrics[]>('/api/v1/metrics/rules', {
    params: {
      sortBy: params?.sortBy ?? 'executionCount',
      sortOrder: params?.sortOrder ?? 'desc',
      limit: params?.limit ?? 10,
    },
  });
  return response.data;
}

/** 获取最近执行日志 */
export async function getRecentLogs(params?: RecentLogsQueryParams): Promise<ExecutionLog[]> {
  const response = await apiClient.get<ExecutionLog[]>('/api/v1/logs/recent', {
    params: {
      limit: params?.limit ?? 20,
      level: params?.level,
    },
  });
  return response.data;
}
