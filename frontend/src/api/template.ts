import apiClient from './client';
import type {
  RuleTemplate,
  CustomTemplate,
  InstantiateTemplateRequest,
  CreateCustomTemplateRequest,
} from '../types/template';
import type { Rule } from '../types/rule';

/** 获取系统模板列表 */
export async function getTemplates(category?: string): Promise<RuleTemplate[]> {
  const response = await apiClient.get<RuleTemplate[]>('/api/v1/templates', {
    params: category ? { category } : undefined,
  });
  return response.data;
}

/** 获取系统模板详情 */
export async function getTemplate(id: number): Promise<RuleTemplate> {
  const response = await apiClient.get<RuleTemplate>(`/api/v1/templates/${id}`);
  return response.data;
}

/** 从系统模板实例化规则 */
export async function instantiateFromTemplate(
  id: number,
  request: InstantiateTemplateRequest,
): Promise<Rule> {
  const response = await apiClient.post<Rule>(
    `/api/v1/templates/${id}/instantiate`,
    request,
  );
  return response.data;
}

/** 获取个人模板列表 */
export async function getCustomTemplates(createdBy?: string): Promise<CustomTemplate[]> {
  const response = await apiClient.get<CustomTemplate[]>('/api/v1/templates/custom', {
    params: createdBy ? { createdBy } : undefined,
  });
  return response.data;
}

/** 保存个人模板 */
export async function saveCustomTemplate(
  request: CreateCustomTemplateRequest,
): Promise<CustomTemplate> {
  const response = await apiClient.post<CustomTemplate>('/api/v1/templates/custom', request);
  return response.data;
}

/** 从个人模板实例化规则 */
export async function instantiateFromCustomTemplate(
  id: number,
  request: InstantiateTemplateRequest,
): Promise<Rule> {
  const response = await apiClient.post<Rule>(
    `/api/v1/templates/custom/${id}/instantiate`,
    request,
  );
  return response.data;
}

/** 删除个人模板 */
export async function deleteCustomTemplate(id: number): Promise<void> {
  await apiClient.delete(`/api/v1/templates/custom/${id}`);
}
