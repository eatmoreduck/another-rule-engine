/**
 * 规则配置类型定义 - V2 支持条件树嵌套
 */

// ============ 基础类型 ============

export type Action = 'PASS' | 'REJECT' | 'MANUAL_REVIEW';

export type Operator = 'EQ' | 'NE' | 'GT' | 'GE' | 'LT' | 'LE' | 'CONTAINS' | 'NOT_CONTAINS' | 'IN' | 'NOT_IN';

// ============ V1 类型（向后兼容） ============

/** @deprecated 使用 RuleGroup + ConditionNode 代替 */
export interface ConditionActionRule {
  fieldName: string;
  operator: Operator;
  threshold: string | number;
  action: Action;
  reason: string;
  logicGate?: 'AND' | 'OR';
}

/** @deprecated 使用 FormRuleConfigV2 代替 */
export interface FormRuleConfig {
  defaultAction: Action;
  defaultReason: string;
  rules: ConditionActionRule[];
}

// ============ V2 条件树类型 ============

/** 原子条件（叶子节点） */
export interface ConditionNode {
  id: string;
  type: 'condition';
  fieldName: string;
  operator: Operator;
  threshold: string | number;
}

/** 逻辑组（递归分支节点） */
export interface LogicGroup {
  id: string;
  type: 'group';
  logic: 'AND' | 'OR';
  children: ConditionTreeNode[];
}

/** 条件树节点联合类型 */
export type ConditionTreeNode = ConditionNode | LogicGroup;

/** 一条规则：条件树 → 动作 */
export interface RuleGroup {
  id: string;
  condition: ConditionTreeNode;
  action: Action;
  reason: string;
}

/** V2 配置（使用条件树） */
export interface FormRuleConfigV2 {
  defaultAction: Action;
  defaultReason: string;
  rules: RuleGroup[];
}

// ============ 常量 ============

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

// ============ 单规则配置 ============

/** 单条规则配置：一个条件树 + 匹配动作 + 默认动作 */
export interface SingleRuleConfig {
  condition: ConditionTreeNode;
  action: Action;
  reason: string;
  defaultAction: Action;
  defaultReason: string;
}

// ============ 工具函数 ============

/** 生成节点 ID */
export function genNodeId(): string {
  return Math.random().toString(36).slice(2, 10);
}

/** 创建一个空的原子条件 */
export function createConditionNode(overrides?: Partial<ConditionNode>): ConditionNode {
  return {
    id: genNodeId(),
    type: 'condition',
    fieldName: '',
    operator: 'GT',
    threshold: '',
    ...overrides,
  };
}

/** 创建一个空的逻辑组 */
export function createLogicGroup(logic: 'AND' | 'OR' = 'AND', children?: ConditionTreeNode[]): LogicGroup {
  return {
    id: genNodeId(),
    type: 'group',
    logic,
    children: children ?? [createConditionNode()],
  };
}

/** 创建一个空的规则组 */
export function createRuleGroup(overrides?: Partial<RuleGroup>): RuleGroup {
  return {
    id: genNodeId(),
    condition: createConditionNode(),
    action: 'REJECT',
    reason: '',
    ...overrides,
  };
}

/** 创建默认的单规则配置 */
export function createDefaultSingleRule(): SingleRuleConfig {
  return {
    condition: createConditionNode(),
    action: 'REJECT',
    reason: '',
    defaultAction: 'PASS',
    defaultReason: '默认通过',
  };
}

/** V1 → V2 迁移 */
export function convertV1ToV2(config: FormRuleConfig): FormRuleConfigV2 {
  return {
    defaultAction: config.defaultAction,
    defaultReason: config.defaultReason,
    rules: config.rules.map((rule) => ({
      id: genNodeId(),
      condition: createConditionNode({
        fieldName: rule.fieldName,
        operator: rule.operator,
        threshold: rule.threshold,
      }),
      action: rule.action,
      reason: rule.reason,
    })),
  };
}
