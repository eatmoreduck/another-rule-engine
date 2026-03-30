import apiClient from './client';
import type {
  GrayscaleConfig,
  GrayscaleRecord,
  GrayscaleReport,
  GrayscaleQueryParams,
} from '../types/grayscale';
import type { PageResponse } from '../types/rule';

/** 创建灰度配置 */
export async function createGrayscale(config: GrayscaleConfig): Promise<GrayscaleRecord> {
  const response = await apiClient.post<GrayscaleRecord>('/api/v1/grayscale', config);
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

/** 查询灰度列表 */
export async function getGrayscales(params?: GrayscaleQueryParams): Promise<PageResponse<GrayscaleRecord>> {
  const response = await apiClient.get<PageResponse<GrayscaleRecord>>('/api/v1/grayscale', {
    params: {
      status: params?.status,
      ruleKey: params?.ruleKey,
      page: params?.page ?? 0,
      size: params?.size ?? 20,
    },
  });
  return response.data;
}
