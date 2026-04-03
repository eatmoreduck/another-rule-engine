/**
 * 流程图配置类型定义 - 流程图模式
 * 对应 Plan 03-03 产出
 */

import type { Node, Edge } from '@xyflow/react';
import type { Action, Operator } from './ruleConfig';

/** 生成简易唯一 ID */
export function genId(): string {
  return Math.random().toString(36).slice(2, 10);
}

// --- 节点数据类型 ---

export interface StartNodeData {
  [key: string]: unknown;
  label: string;
  nodeType: 'start';
}

export interface EndNodeData {
  [key: string]: unknown;
  label: string;
  nodeType: 'end';
  defaultAction: Action;
  defaultReason: string;
}

export interface ConditionNodeData {
  [key: string]: unknown;
  label: string;
  nodeType: 'condition';
  fieldName: string;
  operator: Operator;
  threshold: string | number;
}

export interface ActionNodeData {
  [key: string]: unknown;
  label: string;
  nodeType: 'action';
  action: Action;
  reason: string;
}

/** 规则集节点中的单个条件项 */
export interface RuleSetConditionItem {
  id: string;
  fieldName: string;
  operator: Operator;
  threshold: string | number;
}

export interface RuleSetNodeData {
  [key: string]: unknown;
  label: string;
  nodeType: 'ruleset';
  /** 引用的规则 Key 列表 */
  ruleKeys: string[];
}

// --- 节点类型联合 ---

export type ConditionNode = Node<ConditionNodeData, 'condition'>;
export type ActionNode = Node<ActionNodeData, 'action'>;
export type StartNode = Node<StartNodeData, 'start'>;
export type EndNode = Node<EndNodeData, 'end'>;
export type RuleSetNode = Node<RuleSetNodeData, 'ruleset'>;

export type FlowNode = ConditionNode | ActionNode | StartNode | EndNode | RuleSetNode;

/** 条件分支边的数据 */
export interface ConditionEdgeData {
  [key: string]: unknown;
  label: string;
  /** true = 满足条件分支, false = 不满足条件分支 */
  conditionMet: boolean;
}

export type FlowEdge = Edge<ConditionEdgeData>;

/** 默认起始节点位置 */
export const DEFAULT_START_POSITION = { x: 250, y: 0 };

/** 默认节点间距 */
export const NODE_VERTICAL_GAP = 120;

/** 流程图初始节点 */
export function createInitialNodes(): FlowNode[] {
  return [
    {
      id: 'start-1',
      type: 'start',
      position: { x: 250, y: 0 },
      data: { label: '开始', nodeType: 'start' },
    },
    {
      id: 'end-1',
      type: 'end',
      position: { x: 250, y: 360 },
      data: { label: '结束', nodeType: 'end', defaultAction: 'PASS', defaultReason: '默认通过' },
    },
  ];
}

/** 流程图初始连线 */
export function createInitialEdges(): FlowEdge[] {
  return [
    {
      id: 'e-start-end',
      source: 'start-1',
      target: 'end-1',
    },
  ];
}
