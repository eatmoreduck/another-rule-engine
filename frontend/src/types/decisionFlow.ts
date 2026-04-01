export enum DecisionFlowStatus {
  DRAFT = 'DRAFT',
  ACTIVE = 'ACTIVE',
  ARCHIVED = 'ARCHIVED',
  DELETED = 'DELETED',
}

export interface DecisionFlow {
  id: number;
  flowKey: string;
  flowName: string;
  flowDescription: string | null;
  flowGraph: string;
  version: number;
  status: DecisionFlowStatus;
  createdBy: string;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string | null;
  enabled: boolean;
  optLockVersion: number;
  environmentId: number | null;
}

export interface CreateDecisionFlowRequest {
  flowKey: string;
  flowName: string;
  flowDescription?: string;
  flowGraph: string;
}

export interface UpdateDecisionFlowRequest {
  flowName?: string;
  flowDescription?: string;
  flowGraph?: string;
  changeReason?: string;
}

export interface DecisionFlowQueryParams {
  status?: DecisionFlowStatus;
  keyword?: string;
  enabled?: boolean;
  page?: number;
  size?: number;
}
