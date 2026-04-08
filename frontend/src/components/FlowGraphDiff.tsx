/**
 * FlowGraphDiff - 决策流可视化图形对比组件
 * 两侧 React Flow 画布并排展示，用颜色高亮标识新增/删除/修改的节点和边
 */

import { useMemo, useState, useEffect, useCallback } from 'react';
import { Tag, Space } from 'antd';
import { ReactFlowProvider, ReactFlow, Controls, Background, BackgroundVariant, type NodeTypes } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useTranslation } from 'react-i18next';
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

const IGNORED_NODE_KEYS = new Set([
  'position', 'selected', 'dragging', 'width', 'height',
  'measured', 'positionAbsoluteX', 'positionAbsoluteY',
  'origin', 'expandParent', 'parentId',
]);

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

function isNodeEqual(a: FlowNode, b: FlowNode): boolean {
  if (a.type !== b.type) return false;
  return JSON.stringify(a.data) === JSON.stringify(b.data);
}

function edgeKey(edge: FlowEdge): string {
  return `${edge.source}::${edge.sourceHandle ?? ''}::${edge.target}`;
}

function isEdgeEqual(a: FlowEdge, b: FlowEdge): boolean {
  if (a.source !== b.source || a.target !== b.target) return false;
  if (a.sourceHandle !== b.sourceHandle) return false;
  return JSON.stringify(a.data) === JSON.stringify(b.data);
}

const NODE_STYLES: Record<DiffStatus, React.CSSProperties> = {
  added: { boxShadow: '0 0 0 3px #52c41a, 0 0 12px rgba(82,196,26,0.4)', borderRadius: 8 },
  removed: { boxShadow: '0 0 0 3px #ff4d4f, 0 0 12px rgba(255,77,79,0.4)', borderRadius: 8, opacity: 0.6 },
  modified: { boxShadow: '0 0 0 3px #faad14, 0 0 12px rgba(250,173,20,0.4)', borderRadius: 8 },
  unchanged: {},
};

const EDGE_STYLES: Record<DiffStatus, React.CSSProperties> = {
  added: { stroke: '#52c41a', strokeWidth: 2 },
  removed: { stroke: '#ff4d4f', strokeWidth: 2, strokeDasharray: '5 5' },
  modified: { stroke: '#faad14', strokeWidth: 2 },
  unchanged: {},
};

function computeDiff(oldGraph: string, newGraph: string) {
  const oldParsed = parseFlowGraph(oldGraph);
  const newParsed = parseFlowGraph(newGraph);

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
  oldTitle,
  newTitle,
}: FlowGraphDiffProps) {
  const { t } = useTranslation();
  const resolvedOldTitle = oldTitle ?? t('grayscale.currentVersionTitle');
  const resolvedNewTitle = newTitle ?? t('grayscale.grayscaleVersionTitle');

  const { oldNodes, oldEdges, newNodes, newEdges, summary } = useMemo(
    () => computeDiff(oldFlowGraph, newFlowGraph),
    [oldFlowGraph, newFlowGraph],
  );

  const hasChanges = summary.addedNodes + summary.removedNodes + summary.modifiedNodes
    + summary.addedEdges + summary.removedEdges + summary.modifiedEdges > 0;

  return (
    <div>
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
              {summary.addedNodes > 0 && <Tag color="success">+{summary.addedNodes}</Tag>}
              {summary.removedNodes > 0 && <Tag color="error">-{summary.removedNodes}</Tag>}
              {summary.modifiedNodes > 0 && <Tag color="warning">~{summary.modifiedNodes}</Tag>}
              {summary.addedEdges > 0 && <Tag color="success">+{summary.addedEdges}</Tag>}
              {summary.removedEdges > 0 && <Tag color="error">-{summary.removedEdges}</Tag>}
              {summary.modifiedEdges > 0 && <Tag color="warning">~{summary.modifiedEdges}</Tag>}
            </>
          ) : (
            <Tag color="default">No changes</Tag>
          )}
        </span>
        <Space size="small">
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <span style={{ width: 12, height: 12, borderRadius: 2, background: '#52c41a', display: 'inline-block' }} />
          </span>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <span style={{ width: 12, height: 12, borderRadius: 2, background: '#ff4d4f', display: 'inline-block' }} />
          </span>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <span style={{ width: 12, height: 12, borderRadius: 2, background: '#faad14', display: 'inline-block' }} />
          </span>
        </Space>
      </div>

      <div style={{ display: 'flex', gap: 12 }}>
        <ReactFlowProvider>
          <SingleCanvas nodes={oldNodes} edges={oldEdges} title={resolvedOldTitle} />
        </ReactFlowProvider>
        <ReactFlowProvider>
          <SingleCanvas nodes={newNodes} edges={newEdges} title={resolvedNewTitle} />
        </ReactFlowProvider>
      </div>
    </div>
  );
}
