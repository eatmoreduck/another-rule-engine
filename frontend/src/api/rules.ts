import apiClient from './client';
import type {
  Rule,
  PageResponse,
  CreateRuleRequest,
  UpdateRuleRequest,
  RuleQueryParams,
  ValidateScriptResponse,
} from '../types/rule';

export async function getRules(params?: RuleQueryParams): Promise<PageResponse<Rule>> {
  const response = await apiClient.get<PageResponse<Rule>>('/api/v1/rules', {
    params: {
      page: params?.page ?? 0,
      size: params?.size ?? 20,
    },
  });
  return response.data;
}

export async function queryRules(params?: RuleQueryParams): Promise<PageResponse<Rule>> {
  const response = await apiClient.post<PageResponse<Rule>>('/api/v1/rules/query', {
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

export async function getRule(ruleKey: string): Promise<Rule> {
  const response = await apiClient.get<Rule>(`/api/v1/rules/${ruleKey}`);
  return response.data;
}

export async function createRule(data: CreateRuleRequest): Promise<Rule> {
  const response = await apiClient.post<Rule>('/api/v1/rules', data);
  return response.data;
}

export async function updateRule(ruleKey: string, data: UpdateRuleRequest): Promise<Rule> {
  const response = await apiClient.put<Rule>(`/api/v1/rules/${ruleKey}`, data);
  return response.data;
}

export async function deleteRule(ruleKey: string): Promise<void> {
  await apiClient.delete(`/api/v1/rules/${ruleKey}`);
}

export async function enableRule(ruleKey: string): Promise<Rule> {
  const response = await apiClient.post<Rule>(`/api/v1/rules/${ruleKey}/enable`);
  return response.data;
}

export async function disableRule(ruleKey: string): Promise<Rule> {
  const response = await apiClient.post<Rule>(`/api/v1/rules/${ruleKey}/disable`);
  return response.data;
}

export async function validateScript(groovyScript: string): Promise<ValidateScriptResponse> {
  const response = await apiClient.post<ValidateScriptResponse>('/api/v1/rules/validate', {
    groovyScript,
  });
  return response.data;
}

/** 获取规则列表供规则集节点选择器使用（轻量查询） */
export async function getRulesForSelect(): Promise<Array<{ ruleKey: string; ruleName: string }>> {
  const response = await getRules({ page: 0, size: 200 });
  return response.content.map(r => ({ ruleKey: r.ruleKey, ruleName: r.ruleName }));
}
