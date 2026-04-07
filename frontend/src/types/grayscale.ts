/** 灰度发布状态 */
export enum GrayscaleStatusEnum {
  /** 草稿 */
  DRAFT = 'DRAFT',
  /** 进行中 */
  RUNNING = 'RUNNING',
  /** 已暂停 */
  PAUSED = 'PAUSED',
  /** 已完成 */
  COMPLETED = 'COMPLETED',
  /** 已回滚 */
  ROLLED_BACK = 'ROLLED_BACK',
}

/** 灰度策略类型 */
export enum GrayscaleStrategyType {
  PERCENTAGE = 'PERCENTAGE',
  FEATURE = 'FEATURE',
  WHITELIST = 'WHITELIST',
}

/** 灰度目标类型 */
export enum GrayscaleTargetType {
  RULE = 'RULE',
  DECISION_FLOW = 'DECISION_FLOW',
}

/** 灰度配置（创建请求） */
export interface GrayscaleConfig {
  /** 规则 Key（向后兼容） */
  ruleKey: string;
  /** 目标类型 */
  targetType: GrayscaleTargetType;
  /** 目标 Key */
  targetKey: string;
  /** 灰度版本号 */
  grayscaleVersion: number;
  /** 灰度百分比（0-100） */
  percentage: number;
  /** 灰度描述 */
  description?: string;
  /** 灰度策略类型 */
  strategyType?: GrayscaleStrategyType;
  /** 特征匹配规则 JSON */
  featureRules?: string;
  /** 白名单用户ID（逗号分隔) */
  whitelistIds?: string;
  /** 是否启用双跑 */
  dualRunEnabled?: boolean;
}

/** 灰度发布记录（对应后端 GrayscaleConfigResponse） */
export interface GrayscaleRecord {
  id: number;
  ruleKey: string;
  /** 目标类型 */
  targetType: string;
  /** 目标 Key */
  targetKey: string;
  currentVersion: number;
  grayscaleVersion: number;
  /** 灰度百分比（对应后端 grayscalePercentage） */
  grayscalePercentage: number;
  /** 状态 */
  status: GrayscaleStatusEnum;
  /** 状态描述 */
  statusDescription: string | null;
  /** 策略类型 */
  strategyType: string;
  /** 特征匹配规则 JSON */
  featureRules: string | null;
  /** 白名单用户ID（逗号分隔） */
  whitelistIds: string | null;
  /** 是否启用双跑 */
  dualRunEnabled: boolean | null;
  /** 灰度描述 */
  description: string | null;
  /** 启动时间 */
  startedAt: string | null;
  /** 完成时间 */
  completedAt: string | null;
  createdBy: string;
  createdAt: string;
}

/** 灰度对比报告 - 版本指标 */
export interface VersionMetrics {
  /** 版本号 */
  version: number;
  /** 执行次数 */
  executionCount: number;
  /** 命中次数 */
  hitCount: number;
  /** 错误次数 */
  errorCount: number;
  /** 平均执行耗时（ms） */
  avgExecutionTimeMs: number;
  /** 错误率（百分比） */
  errorRate: number;
  /** 命中率（百分比） */
  hitRate: number;
}

/** 灰度对比报告（对应后端 GrayscaleReportResponse） */
export interface GrayscaleReport {
  configId: number;
  ruleKey: string;
  currentVersion: number;
  grayscaleVersion: number;
  grayscalePercentage: number;
  currentVersionMetrics: VersionMetrics;
  grayscaleVersionMetrics: VersionMetrics;
}

/** 灰度执行日志（对应后端 CanaryExecutionLog） */
export interface CanaryExecutionLog {
  id: number;
  traceId: string;
  targetType: string;
  targetKey: string;
  versionUsed: number;
  isCanary: boolean;
  requestFeatures: string | null;
  decisionResult: string | null;
  executionTimeMs: number | null;
  errorMessage: string | null;
  createdAt: string;
}

/** 灰度执行日志查询参数 */
export interface CanaryLogQueryParams {
  targetType?: string;
  targetKey?: string;
  startTime?: string;
  endTime?: string;
}

/** 灰度列表查询参数 */
export interface GrayscaleQueryParams {
  status?: GrayscaleStatusEnum;
  ruleKey?: string;
  targetType?: GrayscaleTargetType;
  page?: number;
  size?: number;
}
