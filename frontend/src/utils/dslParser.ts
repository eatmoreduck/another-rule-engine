/**
 * Groovy DSL 解析器
 * 将后端存储的 groovyScript 反向解析为 FormRuleConfig
 * 用于：1) 编辑页回填表单  2) 详情页人类可读展示
 * V2: 支持条件树嵌套解析 + 单规则模型
 */

import type {
  FormRuleConfig,
  FormRuleConfigV2,
  ConditionActionRule,
  ConditionTreeNode,
  Action,
  Operator,
  SingleRuleConfig,
} from '../types/ruleConfig';
import {
  createConditionNode,
  createLogicGroup,
  createRuleGroup,
} from '../types/ruleConfig';
import { createDefaultSingleRule } from '../types/ruleConfig';

// ============ 运算符反向映射 ============

/** 运算符反向映射：按优先级排列（多字符运算符优先匹配） */
const OPERATOR_PATTERNS: Array<{
  pattern: RegExp;
  op: Operator;
  extractField: (m: RegExpMatchArray) => { field: string; value: string };
}> = [
  {
    pattern: /!\s*features\.(\w+)\?\.\s*contains\((.+?)\)/,
    op: 'NOT_CONTAINS',
    extractField: (m) => ({ field: m[1], value: m[2] }),
  },
  {
    pattern: /features\.(\w+)\?\.\s*contains\((.+?)\)/,
    op: 'CONTAINS',
    extractField: (m) => ({ field: m[1], value: m[2] }),
  },
  {
    pattern: /!\s*\[(.+?)\]\.contains\(features\.(\w+)\)/,
    op: 'NOT_IN',
    extractField: (m) => ({ field: m[2], value: m[1] }),
  },
  {
    pattern: /\[(.+?)\]\.contains\(features\.(\w+)\)/,
    op: 'IN',
    extractField: (m) => ({ field: m[2], value: m[1] }),
  },
  {
    pattern: /features\.(\w+)\s*!=\s*(.+)/,
    op: 'NE',
    extractField: (m) => ({ field: m[1], value: m[2] }),
  },
  {
    pattern: /features\.(\w+)\s*>=\s*(.+)/,
    op: 'GE',
    extractField: (m) => ({ field: m[1], value: m[2] }),
  },
  {
    pattern: /features\.(\w+)\s*>\s*(.+)/,
    op: 'GT',
    extractField: (m) => ({ field: m[1], value: m[2] }),
  },
  {
    pattern: /features\.(\w+)\s*<=\s*(.+)/,
    op: 'LE',
    extractField: (m) => ({ field: m[1], value: m[2] }),
  },
  {
    pattern: /features\.(\w+)\s*<\s*(.+)/,
    op: 'LT',
    extractField: (m) => ({ field: m[1], value: m[2] }),
  },
  {
    pattern: /features\.(\w+)\s*==\s*(.+)/,
    op: 'EQ',
    extractField: (m) => ({ field: m[1], value: m[2] }),
  },
];

// ============ 辅助函数 ============

/** 解析值：去除引号，尝试转数字 */
function parseValue(raw: string): string | number {
  let trimmed = raw.trim();
  trimmed = trimmed.replace(/[{}]+$/, '').trim();
  if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
    return trimmed.slice(1, -1).replace(/\\'/g, "'");
  }
  const num = Number(trimmed);
  if (!isNaN(num) && trimmed !== '') return num;
  return trimmed;
}

/** 解析 return 语句中的 decision 和 reason */
function parseReturnStatement(line: string): { decision: Action; reason: string } | null {
  const m = line.match(/return\s*\[\s*decision\s*:\s*'(\w+)'s*,\s*reason\s*:\s*'([^']*)'\s*\]/);
  if (!m) return null;
  return { decision: m[1] as Action, reason: m[2].replace(/\\'/g, "'") };
}

/** 解析条件表达式字符串 */
function parseConditionExpression(expr: string): {
  fieldName: string;
  operator: Operator;
  threshold: string | number;
} | null {
  for (const { pattern, op, extractField } of OPERATOR_PATTERNS) {
    const m = expr.match(pattern);
    if (m) {
      const { field, value } = extractField(m);
      return { fieldName: field, operator: op, threshold: parseValue(value) };
    }
  }
  return null;
}

// ============ V1 解析 ============

/**
 * 将 Groovy 脚本解析为 FormRuleConfig (V1 格式)
 */
export function parseGroovyToConfig(script: string): FormRuleConfig {
  const config: FormRuleConfig = {
    defaultAction: 'PASS',
    defaultReason: '默认通过',
    rules: [],
  };

  if (!script || !script.trim()) return config;

  const lines = script.split('\n').map((l) => l.trimEnd());

  let inIfBlock = false;
  let currentCondition: Partial<ConditionActionRule> | null = null;
  let braceDepth = 0;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();

    if (!line || line.startsWith('//') || line.startsWith('def evaluate') || line === '{') {
      continue;
    }
    if (line === '}' && braceDepth === 0) {
      continue;
    }

    const ifMatch = line.match(/^if\s*\((.+)\)\s*\{$/);
    if (ifMatch) {
      inIfBlock = true;
      braceDepth = 1;
      const parsed = parseConditionExpression(ifMatch[1].trim());
      if (parsed) {
        currentCondition = {
          fieldName: parsed.fieldName,
          operator: parsed.operator,
          threshold: parsed.threshold,
        };
      }
      continue;
    }

    if (line.includes('return [decision:')) {
      const ret = parseReturnStatement(line);
      if (!ret) continue;

      if (inIfBlock && currentCondition) {
        config.rules.push({
          fieldName: currentCondition.fieldName ?? '',
          operator: currentCondition.operator ?? 'GT',
          threshold: currentCondition.threshold ?? '',
          action: ret.decision,
          reason: ret.reason,
        });
        currentCondition = null;
      } else {
        config.defaultAction = ret.decision;
        config.defaultReason = ret.reason;
      }
      continue;
    }

    if (line === '} else {' || line === '}else{') {
      braceDepth = 1;
      inIfBlock = false;
      continue;
    }

    if (line === '}') {
      braceDepth = 0;
      inIfBlock = false;
      continue;
    }
  }

  return config;
}

// ============ V1 详情页展示 ============

export interface ParsedRuleDisplay {
  conditions: Array<{
    fieldName: string;
    operatorLabel: string;
    threshold: string;
    actionLabel: string;
    reason: string;
  }>;
  defaultActionLabel: string;
  defaultReason: string;
}

/** 将脚本解析为人类可读的展示数据 (V1) */
export function parseGroovyForDisplay(
  script: string,
  operatorLabels: Record<string, string>,
  actionLabels: Record<string, string>,
): ParsedRuleDisplay {
  const config = parseGroovyToConfig(script);

  return {
    conditions: config.rules.map((rule) => ({
      fieldName: rule.fieldName,
      operatorLabel: operatorLabels[rule.operator] ?? rule.operator,
      threshold: String(rule.threshold),
      actionLabel: actionLabels[rule.action] ?? rule.action,
      reason: rule.reason,
    })),
    defaultActionLabel: actionLabels[config.defaultAction] ?? config.defaultAction,
    defaultReason: config.defaultReason,
  };
}

// ============ V2 条件树解析 ============

/**
 * 将 Groovy if 条件表达式递归解析为条件树
 *
 * 支持 AND (&&) 和 OR (||) 嵌套，AND 优先级高于 OR
 */
function parseConditionTree(expr: string): ConditionTreeNode | null {
  const trimmed = expr.trim();
  if (!trimmed) return null;

  // 尝试按 OR 拆分（OR 优先级低）
  const orParts = splitByOperator(trimmed, '||');
  if (orParts.length > 1) {
    const children = orParts
      .map((p) => parseConditionTree(p))
      .filter((n): n is ConditionTreeNode => n !== null);
    if (children.length === 0) return null;
    if (children.length === 1) return children[0];
    return createLogicGroup('OR', children);
  }

  // 尝试按 AND 拆分
  const andParts = splitByOperator(trimmed, '&&');
  if (andParts.length > 1) {
    const children = andParts
      .map((p) => parseConditionTree(p))
      .filter((n): n is ConditionTreeNode => n !== null);
    if (children.length === 0) return null;
    if (children.length === 1) return children[0];
    return createLogicGroup('AND', children);
  }

  // 去除最外层括号后重试
  if (trimmed.startsWith('(') && trimmed.endsWith(')')) {
    const inner = trimmed.slice(1, -1);
    if (inner.length > 0) {
      return parseConditionTree(inner);
    }
  }

  // 原子条件
  const parsed = parseConditionExpression(trimmed);
  if (parsed) {
    return createConditionNode({
      fieldName: parsed.fieldName,
      operator: parsed.operator,
      threshold: parsed.threshold,
    });
  }

  return null;
}

/**
 * 按指定运算符拆分表达式，尊重括号嵌套
 */
function splitByOperator(expr: string, op: string): string[] {
  const parts: string[] = [];
  let depth = 0;
  let current = '';
  let i = 0;

  while (i < expr.length) {
    const char = expr[i];

    if (char === '(') {
      depth++;
      current += char;
      i++;
    } else if (char === ')') {
      depth--;
      current += char;
      i++;
    } else if (depth === 0 && expr.slice(i).startsWith(op)) {
      // 只在括号外且运算符在当前位置时拆分
      parts.push(current.trim());
      current = '';
      i += op.length;
    } else {
      current += char;
      i++;
    }
  }

  if (current.trim()) {
    parts.push(current.trim());
  }

  return parts;
}

/**
 * 从 Groovy 脚本中提取所有 if-return 对和默认 return
 * 返回 { ifReturn: [{conditionExpr, action, reason}], defaultReturn: {action, reason} }
 */
function extractIfReturnPairs(script: string): {
  rules: Array<{ conditionExpr: string; action: Action; reason: string }>;
  defaultAction: Action;
  defaultReason: string;
} {
  const result = {
    rules: [] as Array<{ conditionExpr: string; action: Action; reason: string }>,
    defaultAction: 'PASS' as Action,
    defaultReason: '默认通过',
  };

  if (!script || !script.trim()) return result;

  const lines = script.split('\n').map((l) => l.trimEnd());
  let braceDepth = 0;
  let currentConditionExpr = '';

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();

    if (!line || line.startsWith('//') || line.startsWith('def evaluate') || line === '{') {
      continue;
    }

    const ifMatch = line.match(/^if\s*\((.+)\)\s*\{$/);
    if (ifMatch) {
      currentConditionExpr = ifMatch[1].trim();
      braceDepth = 1;
      continue;
    }

    if (line.includes('return [decision:')) {
      const ret = parseReturnStatement(line);
      if (ret) {
        if (braceDepth > 0 && currentConditionExpr) {
          result.rules.push({
            conditionExpr: currentConditionExpr,
            action: ret.decision,
            reason: ret.reason,
          });
          currentConditionExpr = '';
        } else {
          result.defaultAction = ret.decision;
          result.defaultReason = ret.reason;
        }
      }
      continue;
    }

    if (line === '} else {' || line === '}else{') {
      braceDepth = 1;
      currentConditionExpr = '';
      continue;
    }

    if (line === '}') {
      braceDepth = 0;
      currentConditionExpr = '';
      continue;
    }
  }

  return result;
}

/**
 * V2: 将 Groovy 脚本解析为 FormRuleConfigV2（条件树格式）
 */
export function parseGroovyToConfigV2(script: string): FormRuleConfigV2 {
  parseGroovyToConfig(script);

  // 如果只有单条件规则，直接映射
  const { rules: ifReturnRules, defaultAction, defaultReason } = extractIfReturnPairs(script);

  if (ifReturnRules.length === 0) {
    return {
      defaultAction,
      defaultReason,
      rules: [],
    };
  }

  // 尝试为每条规则解析条件树
  const ruleGroups = ifReturnRules.map((r) => {
    const conditionTree = parseConditionTree(r.conditionExpr);
    return createRuleGroup({
      condition: conditionTree ?? createConditionNode(),
      action: r.action,
      reason: r.reason,
    });
  });

  return {
    defaultAction,
    defaultReason,
    rules: ruleGroups,
  };
}

// ============ V2 详情页展示 ============

/** 条件树展示节点 */
export interface ConditionTreeDisplayNode {
  type: 'condition' | 'group';
  fieldName?: string;
  operatorLabel?: string;
  threshold?: string;
  logic?: 'AND' | 'OR';
  children?: ConditionTreeDisplayNode[];
}

/** 将条件树转换为展示节点 */
function treeToDisplayNode(
  node: ConditionTreeNode,
  operatorLabels: Record<string, string>,
): ConditionTreeDisplayNode {
  if (node.type === 'condition') {
    return {
      type: 'condition',
      fieldName: node.fieldName,
      operatorLabel: operatorLabels[node.operator] ?? node.operator,
      threshold: String(node.threshold),
    };
  }
  // group
  return {
    type: 'group',
    logic: node.logic,
    children: node.children.map((child) => treeToDisplayNode(child, operatorLabels)),
  };
}

export interface ParsedRuleDisplayV2 {
  rules: Array<{
    conditionTree: ConditionTreeDisplayNode;
    actionLabel: string;
    reason: string;
  }>;
  defaultActionLabel: string;
  defaultReason: string;
}

/** V2: 将脚本解析为条件树展示数据 */
export function parseGroovyForDisplayV2(
  script: string,
  operatorLabels: Record<string, string>,
  actionLabels: Record<string, string>,
): ParsedRuleDisplayV2 {
  const config = parseGroovyToConfigV2(script);

  return {
    rules: config.rules.map((rule) => ({
      conditionTree: treeToDisplayNode(rule.condition, operatorLabels),
      actionLabel: actionLabels[rule.action] ?? rule.action,
      reason: rule.reason,
    })),
    defaultActionLabel: actionLabels[config.defaultAction] ?? config.defaultAction,
    defaultReason: config.defaultReason,
  };
}

// ============ 单规则模型 ============

/**
 * 将 Groovy 脚本解析为单规则配置 (SingleRuleConfig)
 * 取第一条规则的条件/动作 + 默认动作。如果没有规则则返回默认配置。
 *
 * 特殊处理：当条件动作与默认动作相同时（典型 V1 if/else 等值场景），
 * 视为无条件规则，只保留默认动作。
 */
export function parseGroovyToSingleRule(script: string): SingleRuleConfig {
  if (!script || !script.trim()) {
    return createDefaultSingleRule();
  }

  const v2 = parseGroovyToConfigV2(script);

  if (v2.rules.length === 0) {
    return {
      ...createDefaultSingleRule(),
      defaultAction: v2.defaultAction,
      defaultReason: v2.defaultReason,
    };
  }

  const firstRule = v2.rules[0];

  // V1 兼容：如果条件动作与默认动作完全相同，视为无条件规则
  if (firstRule.action === v2.defaultAction && firstRule.reason === v2.defaultReason) {
    return {
      ...createDefaultSingleRule(),
      defaultAction: v2.defaultAction,
      defaultReason: v2.defaultReason,
    };
  }

  return {
    condition: firstRule.condition,
    action: firstRule.action,
    reason: firstRule.reason,
    defaultAction: v2.defaultAction,
    defaultReason: v2.defaultReason,
  };
}

/**
 * 从 ConditionTreeNode 递归提取所有字段名（去重）
 */
export function extractFieldNames(node: ConditionTreeNode): string[] {
  const names = new Set<string>();

  function walk(n: ConditionTreeNode) {
    if (n.type === 'condition') {
      if (n.fieldName && n.fieldName.trim()) {
        names.add(n.fieldName.trim());
      }
    } else if (n.type === 'group') {
      n.children.forEach(walk);
    }
  }

  walk(node);
  return Array.from(names);
}

/**
 * 从完整的 Groovy 脚本中提取所有 features.xxx 字段名（去重）
 * 扫描所有 if 分支和表达式，不局限于单条规则的条件树
 */
export function extractFieldNamesFromScript(script: string): string[] {
  const names = new Set<string>();
  if (!script) return [];

  // 匹配 features.xxx 模式的字段引用
  const regex = /features\.(\w+)/g;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(script)) !== null) {
    names.add(match[1]);
  }

  return Array.from(names);
}

/**
 * 从决策流 flowGraph JSON 中提取所有特征字段名（去重）
 * 策略：正则扫描整个 flowGraph JSON 中的 features.xxx，同时收集条件节点的 fieldName
 */
export function extractFieldNamesFromFlowGraph(flowGraphJson: string): string[] {
  const names = new Set<string>();
  if (!flowGraphJson) return [];

  // 1. 正则扫描 features.xxx（覆盖所有节点类型中可能的引用）
  const regex = /features\.(\w+)/g;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(flowGraphJson)) !== null) {
    names.add(match[1]);
  }

  // 2. 解析 JSON，收集条件节点的 fieldName（更精确）
  try {
    const graph = JSON.parse(flowGraphJson);
    const nodes: Array<{ data?: Record<string, unknown> }> = graph?.nodes ?? [];
    for (const node of nodes) {
      if (node.data?.nodeType === 'condition' && node.data.fieldName) {
        names.add(String(node.data.fieldName));
      }
    }
  } catch {
    // JSON 解析失败，仅靠正则结果
  }

  return Array.from(names);
}

// ============ 字段类型推断 ============

export type FieldInferredType = 'number' | 'string';

export interface FieldTypeInfo {
  name: string;
  inferredType: FieldInferredType;
}

/**
 * 从 Groovy 脚本推断字段类型
 * 策略：分析比较运算符右侧的值类型
 *   features.amount > 1000       → number
 *   features.name == 'test'      → string
 *   features.items?.contains('a') → string
 */
export function extractFieldTypesFromScript(script: string): FieldTypeInfo[] {
  const typeMap = new Map<string, FieldInferredType>();
  if (!script) return [];

  // 数值比较: features.xxx > />= /< /<= /!= /== 数字
  const numCmp = /features\.(\w+)\s*(?:>=|<=|>|<|!=|==)\s*(\d+(?:\.\d+)?)/g;
  let m: RegExpExecArray | null;
  while ((m = numCmp.exec(script)) !== null) {
    typeMap.set(m[1], 'number');
  }

  // 字符串比较: features.xxx == 'string' 或 != 'string'
  const strCmp = /features\.(\w+)\s*(?:==|!=)\s*'[^']*'/g;
  while ((m = strCmp.exec(script)) !== null) {
    typeMap.set(m[1], 'string');
  }

  // contains 操作: features.xxx?.contains(...) → string
  const containsOp = /features\.(\w+)\??\.\s*contains\(/g;
  while ((m = containsOp.exec(script)) !== null) {
    typeMap.set(m[1], 'string');
  }

  // IN 操作: [a, b, c].contains(features.xxx)
  const inOp = /\[(.+?)\]\.contains\(features\.(\w+)\)/g;
  while ((m = inOp.exec(script)) !== null) {
    const firstElem = m[1].split(',')[0].trim();
    typeMap.set(m[2], /^-?\d+(?:\.\d+)?$/.test(firstElem) ? 'number' : 'string');
  }

  // NOT_IN 和 NOT_CONTAINS: !features.xxx?.contains(...) 或 ![...].contains(features.xxx)
  const notContains = /!\s*features\.(\w+)\??\.\s*contains\(/g;
  while ((m = notContains.exec(script)) !== null) {
    typeMap.set(m[1], 'string');
  }
  const notIn = /!\s*\[(.+?)\]\.contains\(features\.(\w+)\)/g;
  while ((m = notIn.exec(script)) !== null) {
    const firstElem = m[1].split(',')[0].trim();
    typeMap.set(m[2], /^-?\d+(?:\.\d+)?$/.test(firstElem) ? 'number' : 'string');
  }

  // 兜底：未推断出类型的字段默认 number
  const allFields = /features\.(\w+)/g;
  while ((m = allFields.exec(script)) !== null) {
    if (!typeMap.has(m[1])) {
      typeMap.set(m[1], 'number');
    }
  }

  return Array.from(typeMap.entries()).map(([name, inferredType]) => ({ name, inferredType }));
}

/**
 * 从决策流 flowGraph JSON 推断字段类型
 * 通过条件节点的 threshold 值类型推断
 */
export function extractFieldTypesFromFlowGraph(flowGraphJson: string): FieldTypeInfo[] {
  const typeMap = new Map<string, FieldInferredType>();
  if (!flowGraphJson) return [];

  try {
    const graph = JSON.parse(flowGraphJson);
    const nodes: Array<{ data?: Record<string, unknown> }> = graph?.nodes ?? [];
    for (const node of nodes) {
      if (node.data?.nodeType === 'condition' && node.data.fieldName) {
        const fieldName = String(node.data.fieldName);
        const threshold = node.data.threshold;
        if (typeof threshold === 'number') {
          typeMap.set(fieldName, 'number');
        } else if (typeof threshold === 'string') {
          // 字符串阈值，但需要排除数字字符串的情况
          const trimmed = (threshold as string).replace(/[{}]+$/, '').trim();
          const unquoted = trimmed.replace(/^'(.*)'$/, '$1');
          typeMap.set(fieldName, isNaN(Number(unquoted)) ? 'string' : 'number');
        } else {
          typeMap.set(fieldName, 'number');
        }
      }
      // 黑名单/白名单节点：keyType 就是 features 中的字段名，类型为 string
      if ((node.data?.nodeType === 'blacklist' || node.data?.nodeType === 'whitelist') && node.data.keyType) {
        const kt = String(node.data.keyType);
        if (!typeMap.has(kt)) {
          typeMap.set(kt, 'string');
        }
      }
    }
  } catch {
    // JSON 解析失败
  }

  // 兜底：扫描 features.xxx
  const allFields = /features\.(\w+)/g;
  let m: RegExpExecArray | null;
  while ((m = allFields.exec(flowGraphJson)) !== null) {
    if (!typeMap.has(m[1])) {
      typeMap.set(m[1], 'number');
    }
  }

  return Array.from(typeMap.entries()).map(([name, inferredType]) => ({ name, inferredType }));
}

/** 单规则展示数据 */
export interface SingleRuleDisplay {
  conditionTree: ConditionTreeDisplayNode;
  actionLabel: string;
  reason: string;
  defaultActionLabel: string;
  defaultReason: string;
}

/**
 * 将 Groovy 脚本解析为单规则展示数据
 */
export function parseSingleRuleForDisplay(
  script: string,
  operatorLabels: Record<string, string>,
  actionLabels: Record<string, string>,
): SingleRuleDisplay {
  const config = parseGroovyToSingleRule(script);

  return {
    conditionTree: treeToDisplayNode(config.condition, operatorLabels),
    actionLabel: actionLabels[config.action] ?? config.action,
    reason: config.reason,
    defaultActionLabel: actionLabels[config.defaultAction] ?? config.defaultAction,
    defaultReason: config.defaultReason,
  };
}
