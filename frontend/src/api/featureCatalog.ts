import apiClient from './client';
import type {
  FeatureCatalogPageResponse,
  FeatureCatalogQueryParams,
  FeatureDefinition,
  FeatureDefinitionRequest,
  FeatureReference,
  FeatureResolvedInfo,
  FeatureValidationItem,
  FeatureValidationResponse,
} from '../types/featureCatalog';

export async function getFeatureDefinitions(params?: FeatureCatalogQueryParams): Promise<FeatureCatalogPageResponse> {
  const { data } = await apiClient.get<FeatureCatalogPageResponse>('/api/v1/features/catalog', {
    params: {
      page: params?.page ?? 0,
      size: params?.size ?? 20,
      keyword: params?.keyword,
      scope: params?.scope,
      dataType: params?.dataType,
      sourceType: params?.sourceType,
      sensitivity: params?.sensitivity,
      status: params?.status,
    },
  });
  return data;
}

export async function getFeatureDefinition(code: string): Promise<FeatureDefinition> {
  const { data } = await apiClient.get<FeatureDefinition>(`/api/v1/features/catalog/${code}`);
  return data;
}

export async function createFeatureDefinition(request: FeatureDefinitionRequest): Promise<FeatureDefinition> {
  const { data } = await apiClient.post<FeatureDefinition>('/api/v1/features/catalog', request);
  return data;
}

export async function updateFeatureDefinition(code: string, request: FeatureDefinitionRequest): Promise<FeatureDefinition> {
  const { data } = await apiClient.put<FeatureDefinition>(`/api/v1/features/catalog/${code}`, request);
  return data;
}

export async function validateFeatureDefinitions(items: FeatureValidationItem[]): Promise<FeatureValidationResponse> {
  const { data } = await apiClient.post<FeatureValidationResponse>('/api/v1/features/catalog/validate', { items });
  return data;
}

export async function getFeatureReferences(code: string): Promise<FeatureReference[]> {
  const { data } = await apiClient.get<FeatureReference[]>(`/api/v1/features/catalog/${code}/references`);
  return data;
}

export async function searchFeatureDefinitions(keyword?: string, limit = 20): Promise<FeatureDefinition[]> {
  const data = await getFeatureDefinitions({ keyword, status: 'ACTIVE', page: 0, size: limit });
  return data.content;
}

export async function resolveFeatureField(fieldName: string): Promise<FeatureResolvedInfo | null> {
  const name = fieldName.trim();
  if (!name) {
    return null;
  }

  const validation = await validateFeatureDefinitions([{ fieldName: name }]);
  const item = validation.items[0];
  if (!item || !item.found || !item.canonicalCode) {
    return null;
  }

  const feature = await getFeatureDefinition(item.canonicalCode);
  return {
    feature,
    matchedByAlias: item.matchedByAlias,
    matchedAlias: item.matchedAlias,
  };
}
