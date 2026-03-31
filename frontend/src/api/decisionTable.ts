import apiClient from './client';
import type {
  DecisionTableRequest,
  DecisionTableResponse,
  DecisionTableValidateResponse,
} from '../types/decisionTable';

/** 决策表转 Groovy 脚本 */
export async function convertToGroovy(request: DecisionTableRequest): Promise<DecisionTableResponse> {
  const response = await apiClient.post<DecisionTableResponse>('/api/v1/decision-table/convert', request);
  return response.data;
}

/** 验证决策表结构 */
export async function validateDecisionTable(request: DecisionTableRequest): Promise<DecisionTableValidateResponse> {
  const response = await apiClient.post<DecisionTableValidateResponse>('/api/v1/decision-table/validate', request);
  return response.data;
}
