/**
 * 规则配置类型定义 - 表单模式
 * 对应 Plan 03-02 产出
 */

export type Action = 'PASS' | 'REJECT' | 'MANUAL_REVIEW';

export type Operator = 'EQ' | 'NE' | 'GT' | 'GE' | 'LT' | 'LE' | 'CONTAINS' | 'NOT_CONTAINS' | 'IN' | 'NOT_IN';

export interface ConditionActionRule {
  /** 特征字段名 */
  fieldName: string;
  /** 比较运算符 */
  operator: Operator;
  /** 比较值 */
  threshold: string | number;
  /** 匹配时执行的动作 */
  action: Action;
  /** 动作原因说明 */
  reason: string;
  /** 逻辑连接词 (AND / OR) */
  logicGate?: 'AND' | 'OR';
}

export interface FormRuleConfig {
  /** 默认动作（所有条件都不匹配时） */
  defaultAction: Action;
  /** 默认动作原因 */
  defaultReason: string;
  /** 条件-动作规则列表 */
  rules: ConditionActionRule[];
}

/** 运算符显示名称映射 */
export const OPERATOR_LABELS: Record<Operator, string> = {
  EQ: '等于',
  NE: '不等于',
  GT: '大于',
  GE: '大于等于',
  LT: '小于',
  LE: '小于等于',
  CONTAINS: '包含',
  NOT_CONTAINS: '不包含',
  IN: '属于',
  NOT_IN: '不属于',
};

/** 动作显示名称映射 */
export const ACTION_LABELS: Record<Action, string> = {
  PASS: '通过',
  REJECT: '拒绝',
  MANUAL_REVIEW: '人工审核',
};
