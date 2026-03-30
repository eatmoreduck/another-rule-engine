/**
 * 表单模式 DSL 生成器
 * 将 FormRuleConfig 转换为 Groovy 脚本
 * 对应 Plan 03-02 产出，Plan 03-04 统一公共函数
 */

import type { FormRuleConfig, ConditionActionRule, Operator } from '../types/ruleConfig';

// --- 运算符映射：UI 运算符 -> Groovy 表达式 ---

const OPERATOR_MAP: Record<Operator, (field: string, value: string | number) => string> = {
  EQ: (f, v) => `features.${f} == ${quoteValue(v)}`,
  NE: (f, v) => `features.${f} != ${quoteValue(v)}`,
  GT: (f, v) => `features.${f} > ${quoteValue(v)}`,
  GE: (f, v) => `features.${f} >= ${quoteValue(v)}`,
  LT: (f, v) => `features.${f} < ${quoteValue(v)}`,
  LE: (f, v) => `features.${f} <= ${quoteValue(v)}`,
  CONTAINS: (f, v) => `features.${f}?.contains(${quoteIfString(v)})`,
  NOT_CONTAINS: (f, v) => `!features.${f}?.contains(${quoteIfString(v)})`,
  IN: (f, v) => {
    const items = String(v).split(',').map((s) => s.trim());
    return `[${items.map((i) => quoteIfString(i)).join(', ')}].contains(features.${f})`;
  },
  NOT_IN: (f, v) => {
    const items = String(v).split(',').map((s) => s.trim());
    return `![${items.map((i) => quoteIfString(i)).join(', ')}].contains(features.${f})`;
  },
};

/** 引用字符串值，数字不引用 */
function quoteValue(value: string | number): string {
  if (typeof value === 'number') return String(value);
  const num = Number(value);
  if (!isNaN(num) && value.trim() !== '') return value;
  return `'${value.replace(/'/g, "\\'")}'`;
}

/** 仅在字符串时加引号（CONTAINS/IN 等场景的右侧值） */
function quoteIfString(value: string | number): string {
  if (typeof value === 'number') return String(value);
  return `'${value.replace(/'/g, "\\'")}'`;
}

// --- 公共函数：供 flowDslGenerator.ts 复用 ---

/**
 * 生成 Groovy 脚本的头部：evaluate 函数定义
 */
export function generateScriptHeader(): string {
  return `def evaluate(Map features) {`;
}

/**
 * 生成 return 语句
 */
export function generateReturnStatement(decision: string, reason: string): string {
  return `  return [decision: '${decision}', reason: '${reason.replace(/'/g, "\\'")}']`;
}

/**
 * 生成脚本尾部闭合大括号
 */
export function generateScriptFooter(): string {
  return `}`;
}

/**
 * 获取运算符的 Groovy 表达式
 */
export function buildConditionExpression(
  fieldName: string,
  operator: Operator,
  threshold: string | number,
): string {
  const mapper = OPERATOR_MAP[operator];
  if (!mapper) {
    throw new Error(`不支持的运算符: ${operator}`);
  }
  return mapper(fieldName, threshold);
}

/**
 * 将单条条件-动作规则转换为 Groovy if 块
 */
function ruleToGroovy(rule: ConditionActionRule, indent: string): string {
  const condition = buildConditionExpression(rule.fieldName, rule.operator, rule.threshold);
  const lines: string[] = [];
  lines.push(`${indent}if (${condition}) {`);
  lines.push(`${indent}  ${generateReturnStatement(rule.action, rule.reason)}`);
  lines.push(`${indent}}`);
  return lines.join('\n');
}

/**
 * 将 FormRuleConfig 转换为完整的 Groovy 脚本
 */
export function generateGroovyFromForm(config: FormRuleConfig): string {
  const lines: string[] = [];
  lines.push(generateScriptHeader());

  if (config.rules.length === 0) {
    lines.push(generateReturnStatement(config.defaultAction, config.defaultReason));
  } else {
    for (const rule of config.rules) {
      lines.push(ruleToGroovy(rule, '  '));
    }
    lines.push('');
    lines.push(`  ${generateReturnStatement(config.defaultAction, config.defaultReason)}`);
  }

  lines.push(generateScriptFooter());
  return lines.join('\n');
}
