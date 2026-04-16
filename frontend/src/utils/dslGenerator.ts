/**
 * 表单模式 DSL 生成器
 * 将 FormRuleConfig / SingleRuleConfig 转换为 Groovy 脚本
 */

import type {
  FormRuleConfig,
  ConditionActionRule,
  ConditionTreeNode,
  LogicGroup,
  Operator,
  SingleRuleConfig,
} from '../types/ruleConfig';

// ============ 运算符映射 ============

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

/** 仅在字符串时加引号 */
function quoteIfString(value: string | number): string {
  if (typeof value === 'number') return String(value);
  return `'${value.replace(/'/g, "\\'")}'`;
}

// ============ 公共函数 ============

/** 生成 Groovy 脚本头部 */
export function generateScriptHeader(): string {
  return `def evaluate(Map features) {`;
}

/** 生成 return 语句 */
export function generateReturnStatement(decision: string, reason: string): string {
  return `  return [decision: '${decision}', reason: '${reason.replace(/'/g, "\\'")}']`;
}

/** 生成脚本尾部 */
export function generateScriptFooter(): string {
  return `}`;
}

/** 获取运算符的 Groovy 表达式 */
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

// ============ V1 生成 ============

/** 将单条条件-动作规则转换为 Groovy if 块 */
function ruleToGroovy(rule: ConditionActionRule, indent: string): string {
  const condition = buildConditionExpression(rule.fieldName, rule.operator, rule.threshold);
  const lines: string[] = [];
  lines.push(`${indent}if (${condition}) {`);
  lines.push(`${indent}  ${generateReturnStatement(rule.action, rule.reason).trim()}`);
  lines.push(`${indent}}`);
  return lines.join('\n');
}

/** V1: 将 FormRuleConfig 转换为完整的 Groovy 脚本 */
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

// ============ V2 条件树表达式生成 ============

/**
 * 将条件树节点转换为 Groovy 表达式字符串
 */
function conditionTreeToExpression(node: ConditionTreeNode): string {
  if (node.type === 'condition') {
    return buildConditionExpression(node.fieldName, node.operator, node.threshold);
  }

  // group
  const group = node as LogicGroup;
  const joiner = group.logic === 'AND' ? ' && ' : ' || ';
  const childExprs = group.children.map((child) => conditionTreeToExpression(child));

  if (childExprs.length === 0) return 'true';
  if (childExprs.length === 1) return childExprs[0];

  // 如果子节点中有 group 类型，需要加括号
  const wrapped = childExprs.map((expr, i) => {
    const child = group.children[i];
    if (child.type === 'group') {
      return `(${expr})`;
    }
    return expr;
  });

  return wrapped.join(joiner);
}

/**
 * 判断条件树是否为空（根节点为空 fieldName 的原子条件）
 */
function isEmptyCondition(node: ConditionTreeNode): boolean {
  if (node.type === 'condition') {
    return !node.fieldName || node.fieldName.trim() === '';
  }
  // group: 如果所有子节点都是空的
  if (node.type === 'group') {
    const group = node as LogicGroup;
    return group.children.length === 0 || group.children.every((child) => isEmptyCondition(child));
  }
  return false;
}

// ============ 单规则生成 ============

/**
 * 将 SingleRuleConfig 转换为完整的 Groovy 脚本
 *
 * 如果条件树为空，只生成默认 return；
 * 否则生成 if (condition) { return ... } + 默认 return
 */
export function generateGroovyFromSingleRule(config: SingleRuleConfig): string {
  const lines: string[] = [];
  lines.push(generateScriptHeader());

  if (isEmptyCondition(config.condition)) {
    // 无条件，直接返回默认动作
    lines.push(generateReturnStatement(config.defaultAction, config.defaultReason));
  } else {
    const expr = conditionTreeToExpression(config.condition);
    lines.push(`  if (${expr}) {`);
    lines.push(`    return [decision: '${config.action}', reason: '${config.reason.replace(/'/g, "\\'")}']`);
    lines.push('  }');
    lines.push(`  ${generateReturnStatement(config.defaultAction, config.defaultReason)}`);
  }

  lines.push(generateScriptFooter());
  return lines.join('\n');
}
