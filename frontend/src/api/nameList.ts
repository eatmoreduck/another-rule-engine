import apiClient from './client';

export interface NameListEntry {
  id: number;
  listType: string;
  keyType: string;
  keyValue: string;
  listKey: string;
  reason: string | null;
  source: string | null;
  expiredAt: string | null;
  createdBy: string;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string | null;
}

export interface CreateNameListEntryRequest {
  listType: string;
  listKey?: string;
  keyType: string;
  keyValue: string;
  reason?: string;
  source?: string;
  expiredAt?: string;
}

export async function getNameListEntries(params?: {
  listKey?: string;
  listType?: string;
  keyType?: string;
  page?: number;
  size?: number;
}): Promise<{ content: NameListEntry[]; totalElements: number }> {
  const response = await apiClient.get('/api/v1/name-list', { params });
  return response.data;
}

export async function createNameListEntry(data: CreateNameListEntryRequest): Promise<NameListEntry> {
  const response = await apiClient.post('/api/v1/name-list', data);
  return response.data;
}

export async function deleteNameListEntry(id: number): Promise<void> {
  await apiClient.delete(`/api/v1/name-list/${id}`);
}

export async function getListKeys(): Promise<string[]> {
  const response = await apiClient.get('/api/v1/name-list/list-keys');
  return response.data;
}
