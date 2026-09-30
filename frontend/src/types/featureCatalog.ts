import type { PageResponse } from './rule';

export type FeatureDataType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'TEXT' | 'LIST' | 'ARRAY' | 'INTEGER' | 'LONG' | 'DOUBLE' | 'DECIMAL';
export type FeatureSourceType = 'INPUT' | 'DERIVED' | 'EXTERNAL' | 'MODEL';
export type FeatureSensitivity = 'NORMAL' | 'SENSITIVE' | 'HIGHLY_SENSITIVE';
export type FeatureStatus = 'ACTIVE' | 'INACTIVE' | 'DEPRECATED';

export interface FeatureDefinition {
  id: number;
  code: string;
  name: string;
  dataType: string;
  sourceType: string;
  exampleValue: string | null;
  description: string | null;
  scope: string | null;
  sensitivity: string;
  status: string;
  owner: string | null;
  createdAt: string;
  updatedAt: string | null;
  aliases: string[];
}

export interface FeatureCatalogQueryParams {
  keyword?: string;
  scope?: string;
  dataType?: string;
  sourceType?: string;
  sensitivity?: string;
  status?: string;
  page?: number;
  size?: number;
}

export interface FeatureDefinitionRequest {
  code: string;
  name: string;
  dataType: string;
  sourceType: string;
  exampleValue?: string;
  description?: string;
  scope?: string;
  sensitivity?: string;
  status?: string;
  owner?: string;
  aliases?: string[];
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
  sensitivity?: string;
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
