/** 环境类型枚举 */
export enum EnvironmentType {
  DEV = 'DEV',
  STAGING = 'STAGING',
  PRODUCTION = 'PRODUCTION',
}

/** 环境类型描述映射 */
export const ENVIRONMENT_TYPE_LABELS: Record<EnvironmentType, { label: string; color: string }> = {
  [EnvironmentType.DEV]: { label: '开发环境', color: 'blue' },
  [EnvironmentType.STAGING]: { label: '预发布环境', color: 'orange' },
  [EnvironmentType.PRODUCTION]: { label: '生产环境', color: 'red' },
};

/** 环境实体 */
export interface Environment {
  id: number;
  name: string;
  type: EnvironmentType;
  description: string | null;
  createdAt: string;
  updatedAt: string | null;
}

/** 环境克隆请求 */
export interface CloneEnvironmentRequest {
  /** 是否覆盖目标环境已有同名规则 */
  overwrite?: boolean;
  /** 操作人 */
  operator?: string;
}

/** 环境克隆响应 */
export interface CloneEnvironmentResponse {
  /** 是否成功 */
  success: boolean;
  /** 克隆的规则数量 */
  clonedCount: number;
  /** 跳过的规则数量 */
  skippedCount: number;
  /** 消息 */
  message: string;
}
