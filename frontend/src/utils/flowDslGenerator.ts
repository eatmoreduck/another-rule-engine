/**
 * 流程图模式 DSL 生成器
 * 将 FlowNode[] + Edge[] 转换为 Groovy 脚本
 * 对应 Plan 03-03 产出，Plan 03-04 引用 dslGenerator 公共函数
 */

import type { Edge } from '@xyflow/react';
import type {
  FlowNode,
  ConditionNodeData,
  ActionNodeData,
  EndNodeData,
  ConditionEdgeData,
} from '../types/flowConfig';
import {
  generateScriptHeader,
  generateReturnStatement,
  generateScriptFooter,
  buildConditionExpression,
} from './dslGenerator';

/** 获取节点数据，带类型保护 */
function getNodeData<T>(node: FlowNode): T {
  return node.data as T;
}

/** 根据边查找目标节点 */
function findTargetNode(
  sourceId: string,
  edges: Edge[],
  nodes: FlowNode[],
  conditionMet: boolean | undefined,
): FlowNode | undefined {
  const matchedEdge = edges.find(
    (e) =>
      e.source === sourceId &&
      (e.data as ConditionEdgeData | undefined)?.conditionMet === conditionMet,
  );
  // 如果没有带条件标记的边，fallback 到第一条出边
  const fallbackEdge = matchedEdge ?? edges.find((e) => e.source === sourceId);
  if (!fallbackEdge) return undefined;
  return nodes.find((n) => n.id === fallbackEdge.target);
}

/** 递归生成条件分支代码 */
function generateConditionBlock(
  node: FlowNode,
  edges: Edge[],
  nodes: FlowNode[],
  indent: string,
  lines: string[],
): void {
  const data = getNodeData<ConditionNodeData>(node);
  const condition = buildConditionExpression(data.fieldName, data.operator, data.threshold);

  // 查找 true 分支和 false 分支的下一个节点
  const trueNode = findTargetNode(node.id, edges, nodes, true);
  const falseNode = findTargetNode(node.id, edges, nodes, false);

  lines.push(`${indent}if (${condition}) {`);
  if (trueNode) {
    processNode(trueNode, edges, nodes, indent + '  ', lines);
  }
  lines.push(`${indent}} else {`);
  if (falseNode) {
    processNode(falseNode, edges, nodes, indent + '  ', lines);
  }
  lines.push(`${indent}}`);
}

/** 处理单个节点，根据类型生成对应代码 */
function processNode(
  node: FlowNode,
  edges: Edge[],
  nodes: FlowNode[],
  indent: string,
  lines: string[],
): void {
  switch (node.data.nodeType) {
    case 'start': {
      // 开始节点 -> 沿出边继续
      const outEdge = edges.find((e) => e.source === node.id);
      if (outEdge) {
        const nextNode = nodes.find((n) => n.id === outEdge.target);
        if (nextNode) processNode(nextNode, edges, nodes, indent, lines);
      }
      break;
    }
    case 'condition': {
      generateConditionBlock(node, edges, nodes, indent, lines);
      break;
    }
    case 'action': {
      const data = getNodeData<ActionNodeData>(node);
      lines.push(`${indent}${generateReturnStatement(data.action, data.reason)}`);
      break;
    }
    case 'end': {
      const data = getNodeData<EndNodeData>(node);
      lines.push(`${indent}${generateReturnStatement(data.defaultAction, data.defaultReason)}`);
      break;
    }
  }
}

/**
 * 将流程图节点和边转换为完整的 Groovy 脚本
 */
export function generateGroovyFromFlow(nodes: FlowNode[], edges: Edge[]): string {
  const lines: string[] = [];

  // 查找起始节点
  const startNode = nodes.find((n) => n.data.nodeType === 'start');
  if (!startNode) {
    return `${generateScriptHeader()}\n  ${generateReturnStatement('PASS', '无起始节点')}\n${generateScriptFooter()}`;
  }

  lines.push(generateScriptHeader());
  processNode(startNode, edges, nodes, '  ', lines);
  lines.push(generateScriptFooter());

  return lines.join('\n');
}
