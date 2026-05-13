export interface Rule {
  id: number;
  ruleKey: string;
  ruleName: string;
  ruleDescription: string | null;
  groovyScript: string;
  version: number;
  createdBy: string;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string | null;
  enabled: boolean;
  deleted: boolean;
  optLockVersion: number;
}

export interface RuleSelectOption {
  ruleKey: string;
  ruleName: string;
  enabled: boolean;
  deleted: boolean;
  unavailable?: boolean;
  version?: number;
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
  keyword?: string;
  enabled?: boolean;
  showDeleted?: boolean;
  page?: number;
  size?: number;
}

export interface ValidateScriptResponse {
  valid: boolean;
  errorMessage?: string;
  errorDetails?: string;
}

/** 规则引用关系 - 规则被哪些决策流/规则集引用 */
export interface RuleReference {
  type: 'decision_flow' | 'rule_set';
  id: string;
  name: string;
  key: string;
  status?: string;
}
