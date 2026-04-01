/**
 * Groovy DSL 解析器
 * 将后端存储的 groovyScript 反向解析为 FormRuleConfig
 * 用于：1) 编辑页回填表单  2) 详情页人类可读展示
 */

import type { FormRuleConfig, ConditionActionRule, Action, Operator } from '../types/ruleConfig';

/** 运算符反向映射：按优先级排列（多字符运算符优先匹配） */
const OPERATOR_PATTERNS: Array<{ pattern: RegExp; op: Operator; extractField: (m: RegExpMatchArray) => { field: string; value: string } }> = [
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

/** 解析值：去除引号，尝试转数字 */
function parseValue(raw: string): string | number {
  let trimmed = raw.trim();
  // 去除尾部 { 或 } 残留
  trimmed = trimmed.replace(/[{}]+$/, '').trim();
  // 去除单引号
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
function parseConditionExpression(expr: string): { fieldName: string; operator: Operator; threshold: string | number } | null {
  for (const { pattern, op, extractField } of OPERATOR_PATTERNS) {
    const m = expr.match(pattern);
    if (m) {
      const { field, value } = extractField(m);
      return { fieldName: field, operator: op, threshold: parseValue(value) };
    }
  }
  return null;
}

/**
 * 将 Groovy 脚本解析为 FormRuleConfig
 *
 * 支持两种脚本格式：
 *
 * 格式1（dslGenerator 产出）:
 * ```
 * def evaluate(Map features) {
 *   if (features.amount > 1000) {
 *     return [decision: 'REJECT', reason: '金额超限']
 *   }
 *   return [decision: 'PASS', reason: '默认通过']
 * }
 * ```
 *
 * 格式2（含 else）:
 * ```
 * def evaluate(Map features) {
 *   if (features.amount > 1000) {
 *     return [decision: 'PASS', reason: '金额正常']
 *   } else {
 *     return [decision: 'REJECT', reason: '金额超限']
 *   }
 * }
 * ```
 */
export function parseGroovyToConfig(script: string): FormRuleConfig {
  const config: FormRuleConfig = {
    defaultAction: 'PASS',
    defaultReason: '默认通过',
    rules: [],
  };

  if (!script || !script.trim()) return config;

  const lines = script.split('\n').map((l) => l.trimEnd());

  // 状态机解析
  let inIfBlock = false;
  let currentCondition: Partial<ConditionActionRule> | null = null;
  let braceDepth = 0;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();

    // 跳过空行、注释、函数定义
    if (!line || line.startsWith('//') || line.startsWith('def evaluate') || line === '{') {
      continue;
    }

    // 外层函数结尾 }
    if (line === '}' && braceDepth === 0) {
      continue;
    }

    // 匹配 if 条件: if (expr) {
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

    // 匹配 return 语句
    if (line.includes('return [decision:')) {
      const ret = parseReturnStatement(line);
      if (!ret) continue;

      if (inIfBlock && currentCondition) {
        // if 块内的 return → 填充条件
        config.rules.push({
          fieldName: currentCondition.fieldName ?? '',
          operator: currentCondition.operator ?? 'GT',
          threshold: currentCondition.threshold ?? '',
          action: ret.decision,
          reason: ret.reason,
        });
        currentCondition = null;
      } else {
        // 独立的 return → 默认动作
        config.defaultAction = ret.decision;
        config.defaultReason = ret.reason;
      }
      continue;
    }

    // } else { → if 块的 else 分支
    if (line === '} else {' || line === '}else{') {
      // else 分支中的 return 将成为默认动作
      braceDepth = 1;
      inIfBlock = false; // else 分支里的 return 是默认行为
      continue;
    }

    // 单独的 } → 关闭当前块
    if (line === '}') {
      braceDepth = 0;
      inIfBlock = false;
      continue;
    }
  }

  return config;
}

// ============ 详情页人类可读解析 ============

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

/** 将脚本解析为人类可读的展示数据 */
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
