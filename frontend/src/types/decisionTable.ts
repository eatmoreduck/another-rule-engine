/** 决策行数据 */
export interface DecisionRow {
  /** 条件值映射 key: 条件名称, value: 条件值 */
  conditions: Record<string, unknown>;
  /** 动作值映射 key: 动作名称, value: 动作值 */
  actions: Record<string, unknown>;
}

/** 决策表转换/验证请求 */
export interface DecisionTableRequest {
  /** 条件列定义 key: 条件名称, value: 条件类型 (STRING, NUMBER, BOOLEAN) */
  conditionColumns: Record<string, string>;
  /** 动作列定义 key: 动作名称, value: 动作类型 */
  actionColumns: Record<string, string>;
  /** 决策规则行 */
  rows: DecisionRow[];
  /** 规则名称（可选） */
  ruleName?: string;
}

/** 决策表转换响应 */
export interface DecisionTableResponse {
  /** 是否成功 */
  success: boolean;
  /** 生成的 Groovy 脚本 */
  groovyScript: string | null;
  /** 错误信息 */
  errorMessage: string | null;
  /** 规则行数 */
  rowCount: number;
}

/** 决策表验证响应 */
export interface DecisionTableValidateResponse {
  /** 是否有效 */
  valid: boolean;
  /** 验证错误列表 */
  errors: string[];
  /** 验证警告列表 */
  warnings: string[];
}
