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

/** 灰度配置（创建请求） */
export interface GrayscaleConfig {
  /** 规则 Key */
  ruleKey: string;
  /** 灰度版本号 */
  grayscaleVersion: number;
  /** 灰度百分比（0-100） */
  percentage: number;
  /** 灰度描述 */
  description?: string;
}

/** 灰度发布记录 */
export interface GrayscaleRecord {
  id: number;
  ruleKey: string;
  ruleName: string;
  grayscaleVersion: number;
  currentVersion: number;
  percentage: number;
  status: GrayscaleStatusEnum;
  description: string | null;
  createdBy: string;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string | null;
}

/** 灰度对比报告 - 版本指标 */
export interface VersionMetrics {
  /** 执行次数 */
  executionCount: number;
  /** 命中次数 */
  hitCount: number;
  /** 命中率（百分比） */
  hitRate: number;
  /** 平均耗时（ms） */
  avgDuration: number;
  /** 最大耗时（ms） */
  maxDuration: number;
  /** 最小耗时（ms） */
  minDuration: number;
}

/** 灰度对比报告 */
export interface GrayscaleReport {
  grayscaleId: number;
  ruleKey: string;
  ruleName: string;
  currentVersion: number;
  grayscaleVersion: number;
  percentage: number;
  status: GrayscaleStatusEnum;
  startTime: string | null;
  duration: string | null;
  currentMetrics: VersionMetrics;
  grayscaleMetrics: VersionMetrics;
}

/** 灰度列表查询参数 */
export interface GrayscaleQueryParams {
  status?: GrayscaleStatusEnum;
  ruleKey?: string;
  page?: number;
  size?: number;
}
