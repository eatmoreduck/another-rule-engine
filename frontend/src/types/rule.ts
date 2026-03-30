export enum RuleStatus {
  DRAFT = 'DRAFT',
  ACTIVE = 'ACTIVE',
  ARCHIVED = 'ARCHIVED',
  DELETED = 'DELETED',
}

export interface Rule {
  id: number;
  ruleKey: string;
  ruleName: string;
  ruleDescription: string | null;
  groovyScript: string;
  version: number;
  status: RuleStatus;
  createdBy: string;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string | null;
  enabled: boolean;
  optLockVersion: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CreateRuleRequest {
  ruleKey: string;
  ruleName: string;
  ruleDescription?: string;
  groovyScript: string;
}

export interface UpdateRuleRequest {
  ruleName?: string;
  ruleDescription?: string;
  groovyScript?: string;
  changeReason?: string;
}

export interface RuleQueryParams {
  status?: RuleStatus;
  keyword?: string;
  enabled?: boolean;
  page?: number;
  size?: number;
}

export interface ValidateScriptResponse {
  valid: boolean;
  errorMessage?: string;
  errorDetails?: string;
}
