/**
 * FlowGraphDiff - 决策流可视化图形对比组件
 * 两侧 React Flow 画布并排展示，用颜色高亮标识新增/删除/修改的节点和边
 */

import { useMemo, useState, useEffect, useCallback } from 'react';
import { Tag, Space } from 'antd';
import { ReactFlowProvider, ReactFlow, Controls, Background, BackgroundVariant, type NodeTypes } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import type { FlowNode, FlowEdge } from '../types/flowConfig';
import { createInitialNodes, createInitialEdges } from '../types/flowConfig';
import StartNodeComponent from './flow/nodes/StartNode';
import EndNodeComponent from './flow/nodes/EndNode';
import ConditionNodeComponent from './flow/nodes/ConditionNode';
import ActionNodeComponent from './flow/nodes/ActionNode';
import RuleSetNodeComponent from './flow/nodes/RuleSetNode';
import BlacklistNodeComponent from './flow/nodes/BlacklistNode';
import WhitelistNodeComponent from './flow/nodes/WhitelistNode';
import MergeNodeComponent from './flow/nodes/MergeNode';

// 复用现有节点组件注册
const nodeTypes: NodeTypes = {
  start: StartNodeComponent,
  end: EndNodeComponent,
  condition: ConditionNodeComponent,
  action: ActionNodeComponent,
  ruleset: RuleSetNodeComponent,
  blacklist: BlacklistNodeComponent,
  whitelist: WhitelistNodeComponent,
  merge: MergeNodeComponent,
};

interface FlowGraphDiffProps {
  oldFlowGraph: string;
  newFlowGraph: string;
  oldTitle?: string;
  newTitle?: string;
}

type DiffStatus = 'added' | 'removed' | 'modified' | 'unchanged';

interface DiffSummary {
  addedNodes: number;
  removedNodes: number;
  modifiedNodes: number;
  addedEdges: number;
  removedEdges: number;
  modifiedEdges: number;
}

// 需要忽略的 node 字段（不影响逻辑）
const IGNORED_NODE_KEYS = new Set([
  'position', 'selected', 'dragging', 'width', 'height',
  'measured', 'positionAbsoluteX', 'positionAbsoluteY',
  'origin', 'expandParent', 'parentId',
]);

/** 安全解析 flowGraph JSON */
function parseFlowGraph(json: string): { nodes: FlowNode[]; edges: FlowEdge[] } {
  try {
    const parsed = JSON.parse(json);
    return {
      nodes: (parsed?.nodes ?? []) as FlowNode[],
      edges: (parsed?.edges ?? []) as FlowEdge[],
    };
  } catch {
    return { nodes: [], edges: [] };
  }
}

/** 比较两个节点是否逻辑相同（忽略位置和运行时字段） */
function isNodeEqual(a: FlowNode, b: FlowNode): boolean {
  if (a.type !== b.type) return false;
  // 比较 data（核心逻辑字段）
  return JSON.stringify(a.data) === JSON.stringify(b.data);
}

/** 边的唯一标识：source + sourceHandle + target */
function edgeKey(edge: FlowEdge): string {
  return `${edge.source}::${edge.sourceHandle ?? ''}::${edge.target}`;
}

/** 比较两条边是否逻辑相同 */
function isEdgeEqual(a: FlowEdge, b: FlowEdge): boolean {
  if (a.source !== b.source || a.target !== b.target) return false;
  if (a.sourceHandle !== b.sourceHandle) return false;
  return JSON.stringify(a.data) === JSON.stringify(b.data);
}

/** 节点 diff 样式 */
const NODE_STYLES: Record<DiffStatus, React.CSSProperties> = {
  added: { boxShadow: '0 0 0 3px #52c41a, 0 0 12px rgba(82,196,26,0.4)', borderRadius: 8 },
  removed: { boxShadow: '0 0 0 3px #ff4d4f, 0 0 12px rgba(255,77,79,0.4)', borderRadius: 8, opacity: 0.6 },
  modified: { boxShadow: '0 0 0 3px #faad14, 0 0 12px rgba(250,173,20,0.4)', borderRadius: 8 },
  unchanged: {},
};

/** 边 diff 样式 */
const EDGE_STYLES: Record<DiffStatus, React.CSSProperties> = {
  added: { stroke: '#52c41a', strokeWidth: 2 },
  removed: { stroke: '#ff4d4f', strokeWidth: 2, strokeDasharray: '5 5' },
  modified: { stroke: '#faad14', strokeWidth: 2 },
  unchanged: {},
};

/** 计算两个版本的 diff */
function computeDiff(oldGraph: string, newGraph: string) {
  const oldParsed = parseFlowGraph(oldGraph);
  const newParsed = parseFlowGraph(newGraph);

  // 节点 diff
  const oldNodeMap = new Map(oldParsed.nodes.map((n) => [n.id, n]));
  const newNodeMap = new Map(newParsed.nodes.map((n) => [n.id, n]));

  const nodeStatus = new Map<string, DiffStatus>();
  for (const [id, node] of oldNodeMap) {
    if (!newNodeMap.has(id)) {
      nodeStatus.set(id, 'removed');
    } else if (isNodeEqual(node, newNodeMap.get(id)!)) {
      nodeStatus.set(id, 'unchanged');
    } else {
      nodeStatus.set(id, 'modified');
    }
  }
  for (const [id] of newNodeMap) {
    if (!oldNodeMap.has(id)) {
      nodeStatus.set(id, 'added');
    }
  }

  // 边 diff
  const oldEdgeMap = new Map(oldParsed.edges.map((e) => [edgeKey(e), e]));
  const newEdgeMap = new Map(newParsed.edges.map((e) => [edgeKey(e), e]));

  const edgeStatus = new Map<string, DiffStatus>();
  for (const [key, edge] of oldEdgeMap) {
    if (!newEdgeMap.has(key)) {
      edgeStatus.set(key, 'removed');
    } else if (isEdgeEqual(edge, newEdgeMap.get(key)!)) {
      edgeStatus.set(key, 'unchanged');
    } else {
      edgeStatus.set(key, 'modified');
    }
  }
  for (const [key] of newEdgeMap) {
    if (!oldEdgeMap.has(key)) {
      edgeStatus.set(key, 'added');
    }
  }

  // 构建带 diff 样式的节点和边
  const oldNodes = oldParsed.nodes.map((n) => {
    const status = nodeStatus.get(n.id) ?? 'unchanged';
    return { ...n, style: NODE_STYLES[status], data: { ...n.data, _diffStatus: status } };
  });
  const newNodes = newParsed.nodes.map((n) => {
    const status = nodeStatus.get(n.id) ?? 'unchanged';
    return { ...n, style: NODE_STYLES[status], data: { ...n.data, _diffStatus: status } };
  });

  const oldEdges = oldParsed.edges.map((e) => {
    const status = edgeStatus.get(edgeKey(e)) ?? 'unchanged';
    return { ...e, style: EDGE_STYLES[status] };
  });
  const newEdges = newParsed.edges.map((e) => {
    const status = edgeStatus.get(edgeKey(e)) ?? 'unchanged';
    return { ...e, style: EDGE_STYLES[status] };
  });

  // 摘要
  let addedNodes = 0, removedNodes = 0, modifiedNodes = 0;
  nodeStatus.forEach((s) => {
    if (s === 'added') addedNodes++;
    else if (s === 'removed') removedNodes++;
    else if (s === 'modified') modifiedNodes++;
  });
  let addedEdges = 0, removedEdges = 0, modifiedEdges = 0;
  edgeStatus.forEach((s) => {
    if (s === 'added') addedEdges++;
    else if (s === 'removed') removedEdges++;
    else if (s === 'modified') modifiedEdges++;
  });

  return {
    oldNodes, oldEdges,
    newNodes, newEdges,
    summary: { addedNodes, removedNodes, modifiedNodes, addedEdges, removedEdges, modifiedEdges },
  };
}

/** 单侧画布组件 */
function SingleCanvas({
  nodes,
  edges,
  title,
}: {
  nodes: FlowNode[];
  edges: FlowEdge[];
  title: string;
}) {
  const [flowEdges, setFlowEdges] = useState<FlowEdge[]>([]);

  // 延迟设置 edges（与 DecisionFlowDetailPage 相同的 workaround）
  useEffect(() => {
    setFlowEdges([]);
    requestAnimationFrame(() => {
      setFlowEdges(edges);
    });
  }, [edges]);

  return (
    <div style={{ flex: 1, minWidth: 0 }}>
      <div style={{
        padding: '6px 12px',
        fontWeight: 600,
        fontSize: 13,
        background: '#fafafa',
        borderBottom: '1px solid #e8e8e8',
        textAlign: 'center',
      }}>
        {title}
      </div>
      <div style={{ height: 420, border: '1px solid #e8e8e8', borderRadius: '0 0 8px 8px', overflow: 'hidden' }}>
        <ReactFlow
          nodes={nodes}
          edges={flowEdges}
          nodeTypes={nodeTypes}
          fitView
          fitViewOptions={{ minZoom: 0.2, maxZoom: 0.8 }}
          nodesDraggable={false}
          nodesConnectable={false}
          edgesReconnectable={false}
          elementsSelectable={false}
          deleteKeyCode={null}
          minZoom={0.1}
          maxZoom={2}
        >
          <Controls showInteractive={false} />
          <Background variant={BackgroundVariant.Dots} gap={16} size={1} />
        </ReactFlow>
      </div>
    </div>
  );
}

export default function FlowGraphDiff({
  oldFlowGraph,
  newFlowGraph,
  oldTitle = '当前版本',
  newTitle = '灰度版本',
}: FlowGraphDiffProps) {
  const { oldNodes, oldEdges, newNodes, newEdges, summary } = useMemo(
    () => computeDiff(oldFlowGraph, newFlowGraph),
    [oldFlowGraph, newFlowGraph],
  );

  const hasChanges = summary.addedNodes + summary.removedNodes + summary.modifiedNodes
    + summary.addedEdges + summary.removedEdges + summary.modifiedEdges > 0;

  return (
    <div>
      {/* 变更摘要 + 图例 */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 8,
        padding: '8px 12px',
        background: '#fafafa',
        borderRadius: 6,
        fontSize: 13,
      }}>
        <span>
          {hasChanges ? (
            <>
              {summary.addedNodes > 0 && <Tag color="success">新增 {summary.addedNodes} 个节点</Tag>}
              {summary.removedNodes > 0 && <Tag color="error">删除 {summary.removedNodes} 个节点</Tag>}
              {summary.modifiedNodes > 0 && <Tag color="warning">修改 {summary.modifiedNodes} 个节点</Tag>}
              {summary.addedEdges > 0 && <Tag color="success">新增 {summary.addedEdges} 条连线</Tag>}
              {summary.removedEdges > 0 && <Tag color="error">删除 {summary.removedEdges} 条连线</Tag>}
              {summary.modifiedEdges > 0 && <Tag color="warning">修改 {summary.modifiedEdges} 条连线</Tag>}
            </>
          ) : (
            <Tag color="default">两个版本完全相同</Tag>
          )}
        </span>
        <Space size="small">
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <span style={{ width: 12, height: 12, borderRadius: 2, background: '#52c41a', display: 'inline-block' }} />
            <span style={{ color: '#666' }}>新增</span>
          </span>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <span style={{ width: 12, height: 12, borderRadius: 2, background: '#ff4d4f', display: 'inline-block' }} />
            <span style={{ color: '#666' }}>删除</span>
          </span>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <span style={{ width: 12, height: 12, borderRadius: 2, background: '#faad14', display: 'inline-block' }} />
            <span style={{ color: '#666' }}>修改</span>
          </span>
        </Space>
      </div>

      {/* 并排画布 */}
      <div style={{ display: 'flex', gap: 12 }}>
        <ReactFlowProvider>
          <SingleCanvas nodes={oldNodes} edges={oldEdges} title={oldTitle} />
        </ReactFlowProvider>
        <ReactFlowProvider>
          <SingleCanvas nodes={newNodes} edges={newEdges} title={newTitle} />
        </ReactFlowProvider>
      </div>
    </div>
  );
}
