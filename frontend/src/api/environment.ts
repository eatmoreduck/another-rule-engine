import apiClient from './client';
import type {
  Environment,
  CloneEnvironmentRequest,
  CloneEnvironmentResponse,
} from '../types/environment';

/** 获取环境列表 */
export async function listEnvironments(): Promise<Environment[]> {
  const response = await apiClient.get<Environment[]>('/api/v1/environments');
  return response.data;
}

/** 获取环境详情 */
export async function getEnvironment(id: number): Promise<Environment> {
  const response = await apiClient.get<Environment>(`/api/v1/environments/${id}`);
  return response.data;
}

/** 克隆环境规则 */
export async function cloneEnvironment(
  fromId: string,
  toId: string,
  request?: CloneEnvironmentRequest,
): Promise<CloneEnvironmentResponse> {
  const response = await apiClient.post<CloneEnvironmentResponse>(
    `/api/v1/environments/${fromId}/clone/${toId}`,
    request,
  );
  return response.data;
}
