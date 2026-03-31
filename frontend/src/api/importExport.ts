import apiClient from './client';
import type { RuleExportData, ImportRulesResponse } from '../types/importExport';

/** 导出所有规则 */
export async function exportAllRules(): Promise<RuleExportData> {
  const response = await apiClient.get<RuleExportData>('/api/v1/export/rules');
  return response.data;
}

/** 导出单条规则 */
export async function exportRule(ruleKey: string): Promise<RuleExportData> {
  const response = await apiClient.get<RuleExportData>(`/api/v1/export/rules/${ruleKey}`);
  return response.data;
}

/** 批量导出规则 */
export async function exportRulesBatch(ruleKeys: string[]): Promise<RuleExportData> {
  const response = await apiClient.post<RuleExportData>('/api/v1/export/rules/batch', ruleKeys);
  return response.data;
}

/** 导入规则 */
export async function importRules(data: RuleExportData): Promise<ImportRulesResponse> {
  const response = await apiClient.post<ImportRulesResponse>('/api/v1/import/rules', data);
  return response.data;
}
