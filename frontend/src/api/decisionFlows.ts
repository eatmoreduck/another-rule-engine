import apiClient from './client';
import type {
  DecisionFlow,
  CreateDecisionFlowRequest,
  UpdateDecisionFlowRequest,
  DecisionFlowQueryParams,
} from '../types/decisionFlow';
import type { PageResponse } from '../types/rule';

export async function getDecisionFlows(params?: DecisionFlowQueryParams): Promise<PageResponse<DecisionFlow>> {
  const response = await apiClient.get<PageResponse<DecisionFlow>>('/api/v1/decision-flows', {
    params: {
      page: params?.page ?? 0,
      size: params?.size ?? 20,
    },
  });
  return response.data;
}

export async function queryDecisionFlows(params?: DecisionFlowQueryParams): Promise<PageResponse<DecisionFlow>> {
  const response = await apiClient.post<PageResponse<DecisionFlow>>('/api/v1/decision-flows/query', {
    status: params?.status,
    keyword: params?.keyword,
    enabled: params?.enabled,
  }, {
    params: {
      page: params?.page ?? 0,
      size: params?.size ?? 20,
    },
  });
  return response.data;
}

export async function getDecisionFlow(flowKey: string): Promise<DecisionFlow> {
  const response = await apiClient.get<DecisionFlow>(`/api/v1/decision-flows/${flowKey}`);
  return response.data;
}

export async function createDecisionFlow(data: CreateDecisionFlowRequest): Promise<DecisionFlow> {
  const response = await apiClient.post<DecisionFlow>('/api/v1/decision-flows', data);
  return response.data;
}

export async function updateDecisionFlow(flowKey: string, data: UpdateDecisionFlowRequest): Promise<DecisionFlow> {
  const response = await apiClient.put<DecisionFlow>(`/api/v1/decision-flows/${flowKey}`, data);
  return response.data;
}

export async function deleteDecisionFlow(flowKey: string): Promise<void> {
  await apiClient.delete(`/api/v1/decision-flows/${flowKey}`);
}

export async function enableDecisionFlow(flowKey: string): Promise<DecisionFlow> {
  const response = await apiClient.post<DecisionFlow>(`/api/v1/decision-flows/${flowKey}/enable`);
  return response.data;
}

export async function disableDecisionFlow(flowKey: string): Promise<DecisionFlow> {
  const response = await apiClient.post<DecisionFlow>(`/api/v1/decision-flows/${flowKey}/disable`);
  return response.data;
}
