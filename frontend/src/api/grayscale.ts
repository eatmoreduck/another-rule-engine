import apiClient from './client';
import type {
  GrayscaleConfig,
  GrayscaleRecord,
  GrayscaleReport,
  GrayscaleQueryParams,
  CanaryExecutionLog,
  CanaryLogQueryParams,
} from '../types/grayscale';
import type { PageResponse } from '../types/rule';

/** 创建灰度配置（支持规则和决策流） */
export async function createGrayscale(config: GrayscaleConfig): Promise<GrayscaleRecord> {
  const requestBody: Record<string, unknown> = {
    targetType: config.targetType,
    grayscaleVersion: config.grayscaleVersion,
    grayscalePercentage: config.percentage,
    strategyType: config.strategyType,
    featureRules: config.featureRules,
    whitelistIds: config.whitelistIds,
    dualRunEnabled: config.dualRunEnabled,
  };

  if (config.targetType === 'DECISION_FLOW') {
    requestBody.targetKey = config.targetKey;
    requestBody.ruleKey = config.targetKey;
  } else {
    requestBody.ruleKey = config.ruleKey;
    requestBody.targetKey = config.targetKey || config.ruleKey;
  }

  const response = await apiClient.post<GrayscaleRecord>('/api/v1/grayscale', requestBody);
  return response.data;
}

/** 查询灰度配置列表（分页） */
export async function getGrayscales(
  params?: GrayscaleQueryParams,
): Promise<PageResponse<GrayscaleRecord>> {
  const queryParams: Record<string, unknown> = {};
  if (params?.status) queryParams.status = params.status;
  if (params?.ruleKey) queryParams.ruleKey = params.ruleKey;
  if (params?.targetType) queryParams.targetType = params.targetType;
  if (params?.page !== undefined) queryParams.page = params.page;
  if (params?.size !== undefined) queryParams.size = params.size;

  const response = await apiClient.get<PageResponse<GrayscaleRecord>>('/api/v1/grayscale', {
    params: queryParams,
  });
  return response.data;
}

/** 启动灰度 */
export async function startGrayscale(id: number): Promise<GrayscaleRecord> {
  const response = await apiClient.put<GrayscaleRecord>(`/api/v1/grayscale/${id}/start`);
  return response.data;
}

/** 暂停灰度 */
export async function pauseGrayscale(id: number): Promise<GrayscaleRecord> {
  const response = await apiClient.put<GrayscaleRecord>(`/api/v1/grayscale/${id}/pause`);
  return response.data;
}

/** 完成灰度（全量发布） */
export async function completeGrayscale(id: number): Promise<GrayscaleRecord> {
  const response = await apiClient.put<GrayscaleRecord>(`/api/v1/grayscale/${id}/complete`);
  return response.data;
}

/** 回滚灰度 */
export async function rollbackGrayscale(id: number): Promise<GrayscaleRecord> {
  const response = await apiClient.put<GrayscaleRecord>(`/api/v1/grayscale/${id}/rollback`);
  return response.data;
}

/** 获取灰度对比报告 */
export async function getGrayscaleReport(id: number): Promise<GrayscaleReport> {
  const response = await apiClient.get<GrayscaleReport>(`/api/v1/grayscale/${id}/report`);
  return response.data;
}

/** 查询灰度执行日志 */
export async function getCanaryLogs(
  params: CanaryLogQueryParams,
): Promise<CanaryExecutionLog[]> {
  const queryParams: Record<string, unknown> = {};
  if (params.targetType) queryParams.targetType = params.targetType;
  if (params.targetKey) queryParams.targetKey = params.targetKey;
  if (params.startTime) queryParams.startTime = params.startTime;
  if (params.endTime) queryParams.endTime = params.endTime;

  const response = await apiClient.get<CanaryExecutionLog[]>(
    '/api/v1/canary-logs',
    { params: queryParams },
  );
  return response.data;
}
