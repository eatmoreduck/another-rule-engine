/**
 * FlowGraphDiff - 决策流可视化图形对比组件
 * 两侧 React Flow 画布并排展示，用颜色高亮标识新增/删除/修改的节点和边
 */

import { useMemo, useState, useEffect } from 'react';
import { Tag, Space } from 'antd';
import { ReactFlowProvider, ReactFlow, Controls, Background, BackgroundVariant, type NodeTypes } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useTranslation } from 'react-i18next';
import type { FlowNode, FlowEdge } from '../types/flowConfig';
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

  const summary: DiffSummary = {
    addedNodes,
    removedNodes,
    modifiedNodes,
    addedEdges,
    removedEdges,
    modifiedEdges,
  };

  return {
    oldNodes, oldEdges,
    newNodes, newEdges,
    summary,
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
