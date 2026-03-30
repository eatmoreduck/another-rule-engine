/** 监控总览数据 */
export interface MetricsOverview {
  /** 总执行次数 */
  totalExecutions: number;
  /** 命中次数（规则条件满足的次数） */
  hitCount: number;
  /** 命中率，0-100 的百分比 */
  hitRate: number;
  /** 平均执行耗时（毫秒） */
  avgExecutionTime: number;
  /** 错误次数 */
  errorCount: number;
  /** 错误率，0-100 的百分比 */
  errorRate: number;
}

/** 单条规则的执行指标 */
export interface RuleMetrics {
  /** 规则 Key */
  ruleKey: string;
  /** 规则名称 */
  ruleName: string;
  /** 执行次数 */
  executionCount: number;
  /** 命中次数 */
  hitCount: number;
  /** 命中率，0-100 的百分比 */
  hitRate: number;
  /** 平均执行耗时（毫秒） */
  avgExecutionTime: number;
  /** 错误次数 */
  errorCount: number;
  /** 是否启用 */
  enabled: boolean;
}

/** 执行日志级别 */
export enum LogLevel {
  INFO = 'INFO',
  WARN = 'WARN',
  ERROR = 'ERROR',
}

/** 执行日志记录 */
export interface ExecutionLog {
  /** 日志 ID */
  id: number;
  /** 规则 Key */
  ruleKey: string;
  /** 规则名称 */
  ruleName: string;
  /** 执行结果：命中 / 未命中 / 错误 */
  result: 'HIT' | 'MISS' | 'ERROR';
  /** 执行耗时（毫秒） */
  executionTime: number;
  /** 日志级别 */
  level: LogLevel;
  /** 错误信息（仅 result 为 ERROR 时存在） */
  errorMessage?: string;
  /** 执行时间戳 */
  executedAt: string;
}

/** 规则指标查询参数 */
export interface RuleMetricsQueryParams {
  /** 排序字段，默认 executionCount */
  sortBy?: 'executionCount' | 'avgExecutionTime' | 'hitRate' | 'errorCount';
  /** 排序方向，默认 desc */
  sortOrder?: 'asc' | 'desc';
  /** 返回数量限制，默认 10 */
  limit?: number;
}

/** 最近日志查询参数 */
export interface RecentLogsQueryParams {
  /** 返回数量限制，默认 20 */
  limit?: number;
  /** 日志级别过滤 */
  level?: LogLevel;
}
