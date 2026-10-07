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

/** 分支内单个子条件 */
export interface ConditionItem {
  fieldName: string;
  operator: Operator;
  threshold: string | number;
}

/**
 * 条件节点的单个分支：组内子条件按 match(ALL=全部满足/ANY=任一满足)聚合，
 * 命中走 sourceHandle=id 的出边，全不命中走兜底(elseHandle)。
 * 旧契约兼容:conditions 为空时由 fieldName/operator/threshold 归一化为单条件。
 */
export interface ConditionBranch {
  id: string;
  match?: 'ALL' | 'ANY';
  conditions?: ConditionItem[];
  /** 旧单条件契约字段，仅旧图加载时存在 */
  fieldName?: string;
  operator?: Operator;
  threshold?: string | number;
}

export interface ConditionNodeData {
  [key: string]: unknown;
  label: string;
  nodeType: 'condition';
  branches: ConditionBranch[];
  /** 旧单条件契约字段，仅旧图加载时存在 */
  fieldName?: string;
  operator?: Operator;
  threshold?: string | number;
}

/** 分支内子条件归一化：conditions 为空时由旧单条件字段构造 */
export function normalizeBranchConditions(branch: ConditionBranch): ConditionItem[] {
  if (branch.conditions?.length) return branch.conditions;
  if (branch.fieldName) {
    return [
      {
        fieldName: branch.fieldName,
        operator: branch.operator ?? 'GT',
        threshold: branch.threshold ?? 0,
      },
    ];
  }
  return [];
}

/** 旧单条件图归一化：无 branches 时由 fieldName/operator/threshold 构造单分支（id=true） */
export function normalizeConditionBranches(data?: ConditionNodeData): ConditionBranch[] {
  if (!data) return [];
  if (data.branches?.length) {
    return data.branches.map((b) => ({
      ...b,
      match: b.match ?? 'ALL',
      conditions: normalizeBranchConditions(b),
    }));
  }
  if (data.fieldName) {
    return [
      {
        id: 'true',
        match: 'ALL',
        conditions: [
          {
            fieldName: data.fieldName,
            operator: data.operator ?? 'GT',
            threshold: data.threshold ?? 0,
          },
        ],
      },
    ];
  }
  return [];
}

/** 条件节点的兜底出边 handle：旧单条件图为 false，多分支图为 else */
export function conditionElseHandle(data?: ConditionNodeData): string {
  return data?.branches?.length ? 'else' : 'false';
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

export interface BlacklistNodeData {
  [key: string]: unknown;
  label: string;
  nodeType: 'blacklist';
  keyType: string; // 'ID_NO' | 'DEVICE_ID' | 'IP' | 'PHONE_NO' | 'MAC_ADDR'
  listKey?: string;
}

export interface WhitelistNodeData {
  [key: string]: unknown;
  label: string;
  nodeType: 'whitelist';
  keyType: string;
  listKey?: string;
}

export interface MergeNodeData {
  [key: string]: unknown;
  label: string;
  nodeType: 'merge';
}

// --- 节点类型联合 ---

export type ConditionNode = Node<ConditionNodeData, 'condition'>;
export type ActionNode = Node<ActionNodeData, 'action'>;
export type StartNode = Node<StartNodeData, 'start'>;
export type EndNode = Node<EndNodeData, 'end'>;
export type RuleSetNode = Node<RuleSetNodeData, 'ruleset'>;
export type BlacklistNode = Node<BlacklistNodeData, 'blacklist'>;
export type WhitelistNode = Node<WhitelistNodeData, 'whitelist'>;
export type MergeNode = Node<MergeNodeData, 'merge'>;

export type FlowNode = ConditionNode | ActionNode | StartNode | EndNode | RuleSetNode | BlacklistNode | WhitelistNode | MergeNode;

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

/** 名单键类型标签映射 */
export const KEY_TYPE_LABELS: Record<string, string> = {
  ID_NO: '身份证号',
  DEVICE_ID: '设备ID',
  IP: 'IP地址',
  PHONE_NO: '手机号',
  MAC_ADDR: 'MAC地址',
};

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
