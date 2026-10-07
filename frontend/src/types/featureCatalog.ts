import type { PageResponse } from './rule';

export type FeatureDataType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'TEXT' | 'LIST' | 'ARRAY' | 'INTEGER' | 'LONG' | 'DOUBLE' | 'DECIMAL';
export type FeatureSourceType = 'INPUT' | 'DERIVED' | 'EXTERNAL' | 'MODEL';
export type FeatureStatus = 'ACTIVE' | 'INACTIVE' | 'DEPRECATED';

export interface FeatureDefinition {
  id: number;
  code: string;
  name: string;
  dataType: string;
  sourceType: string;
  exampleValue: string | null;
  expression: string | null;
  description: string | null;
  status: string;
  owner: string | null;
  createdAt: string;
  updatedAt: string | null;
  deleted: boolean;
  aliases: string[];
}

export interface FeatureCatalogQueryParams {
  keyword?: string;
  dataType?: string;
  sourceType?: string;
  status?: string;
  includeDeleted?: boolean;
  page?: number;
  size?: number;
}

export interface FeatureDefinitionRequest {
  code: string;
  name: string;
  dataType: string;
  sourceType: string;
  exampleValue?: string;
  expression?: string;
  description?: string;
  status?: string;
  owner?: string;
  aliases?: string[];
}

/** 衍生特征公式试算响应 */
export interface FeatureExpressionTestResponse {
  ok: boolean;
  variables: string[];
  result?: unknown;
  error?: string | null;
}

export interface FeatureValidationItem {
  fieldName: string;
  operator?: string;
  threshold?: unknown;
}

export interface FeatureValidationItemResult {
  fieldName: string;
  found: boolean;
  matchedByAlias: boolean;
  canonicalCode?: string;
  matchedAlias?: string;
  dataType?: string;
  sourceType?: string;
  warnings: string[];
}

export interface FeatureValidationResponse {
  valid: boolean;
  warnings: string[];
  unknownFields: string[];
  items: FeatureValidationItemResult[];
}

export interface FeatureResolvedInfo {
  feature: FeatureDefinition;
  matchedByAlias: boolean;
  matchedAlias?: string;
}

export interface FeatureReference {
  type: 'rule' | 'decision_flow';
  id: number | string;
  name: string;
  key: string;
}

export type FeatureCatalogPageResponse = PageResponse<FeatureDefinition>;
