import apiClient from './client';
import type {
  Rule,
  PageResponse,
  CreateRuleRequest,
  UpdateRuleRequest,
  RuleQueryParams,
  ValidateScriptResponse,
  RuleReference,
  RuleSelectOption,
} from '../types/rule';

export async function getRules(params?: RuleQueryParams): Promise<PageResponse<Rule>> {
  const response = await apiClient.get<PageResponse<Rule>>('/api/v1/rules', {
    params: {
      page: params?.page ?? 0,
      size: params?.size ?? 20,
      keyword: params?.keyword,
      enabled: params?.enabled,
      showDeleted: params?.showDeleted,
    },
  });
  return response.data;
}

export async function queryRules(params?: RuleQueryParams): Promise<PageResponse<Rule>> {
  const response = await apiClient.post<PageResponse<Rule>>('/api/v1/rules/query', {
    keyword: params?.keyword,
    enabled: params?.enabled,
    showDeleted: params?.showDeleted,
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

/** 获取规则列表供规则集节点选择器使用（编辑态可见全部未删除规则，执行态由后端判定是否可用） */
export async function getRulesForSelect(): Promise<RuleSelectOption[]> {
  // 使用单次请求 + 大 pageSize 简化逻辑，避免 N+1 问题
  // 如果未来规则数量超过 500，考虑后端添加轻量查询端点
  const response = await getRules({ page: 0, size: 500, showDeleted: false });
  return response.content
    .map((rule) => ({
      ruleKey: rule.ruleKey,
      ruleName: rule.ruleName,
      enabled: rule.enabled,
      deleted: rule.deleted,
      version: rule.version,
    }))
    .sort((left, right) => {
      if (left.enabled !== right.enabled) {
        return left.enabled ? -1 : 1;
      }
      return left.ruleName.localeCompare(right.ruleName, 'zh-CN');
    });
}

/** 查询规则被哪些决策流/规则集引用 */
export async function getRuleReferences(ruleKey: string): Promise<RuleReference[]> {
  const response = await apiClient.get<RuleReference[]>(`/api/v1/rules/${ruleKey}/references`);
  return response.data;
}
