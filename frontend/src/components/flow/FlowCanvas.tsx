/**
 * FlowCanvas - React Flow 画布组件
 * 集成拖拽添加节点、连接节点、自定义节点渲染
 */

import { useCallback, type DragEvent } from 'react';
import {
  ReactFlow,
  Controls,
  Background,
  useReactFlow,
  type Connection,
  type NodeTypes,
  type OnNodesChange,
  type OnEdgesChange,
  BackgroundVariant,
} from '@xyflow/react';
import type { FlowNode, FlowEdge } from '../../types/flowConfig';

import { genId } from '../../types/flowConfig';

import StartNodeComponent from './nodes/StartNode';
import EndNodeComponent from './nodes/EndNode';
import ConditionNodeComponent from './nodes/ConditionNode';
import ActionNodeComponent from './nodes/ActionNode';
import RuleSetNodeComponent from './nodes/RuleSetNode';

const nodeTypes: NodeTypes = {
  start: StartNodeComponent,
  end: EndNodeComponent,
  condition: ConditionNodeComponent,
  action: ActionNodeComponent,
  ruleset: RuleSetNodeComponent,
};

function getDefaultConditionData() {
  return {
    label: '条件判断',
    nodeType: 'condition' as const,
    fieldName: '',
    operator: 'GT' as const,
    threshold: 0,
  };
}

function getDefaultActionData() {
  return {
    label: '决策结果',
    nodeType: 'action' as const,
    action: 'REJECT' as const,
    reason: '',
  };
}
function getDefaultRuleSetData() {
  return {
    label: '规则集',
    nodeType: 'ruleset' as const,
    ruleKeys: [] as string[],
  };
}

interface FlowCanvasProps {
  nodes: FlowNode[];
  edges: FlowEdge[];
  onNodesChange: OnNodesChange<FlowNode>;
  onEdgesChange: OnEdgesChange<FlowEdge>;
  onConnect: (connection: Connection) => void;
  onNodeDoubleClick: (event: React.MouseEvent, node: FlowNode) => void;
}

export default function FlowCanvas({
  nodes,
  edges,
  onNodesChange,
  onEdgesChange,
  onConnect: onConnectProp,
  onNodeDoubleClick,
}: FlowCanvasProps) {
  const { screenToFlowPosition, addNodes } = useReactFlow();
  const onDragOver = useCallback((event: DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback(
    (event: DragEvent) => {
      event.preventDefault();
      const type = event.dataTransfer.getData('application/reactflow');
      if (!type) return;
      const position = screenToFlowPosition({
        x: event.clientX,
        y: event.clientY,
      });
      let newNode: FlowNode;
      if (type === 'condition') {
        newNode = {
          id: genId(),
          type: 'condition',
          position,
          data: getDefaultConditionData(),
        };
      } else if (type === 'action') {
        newNode = {
          id: genId(),
          type: 'action',
          position,
          data: getDefaultActionData(),
        };
      } else if (type === 'ruleset') {
        newNode = {
          id: genId(),
          type: 'ruleset',
          position,
          data: getDefaultRuleSetData(),
        };
      } else {
        return;
      }
      addNodes(newNode);
    },
    [screenToFlowPosition, addNodes],
  );
  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      onConnect={onConnectProp}
      onDrop={onDrop}
      onDragOver={onDragOver}
      onNodeDoubleClick={onNodeDoubleClick as never}
      nodeTypes={nodeTypes}
      fitView
      fitViewOptions={{ minZoom: 0.3, maxZoom: 0.8 }}
      minZoom={0.2}
      maxZoom={2}
      snapToGrid
      snapGrid={[15, 15] as [number, number]}
      deleteKeyCode={['Backspace', 'Delete']}
    >
      <Controls />
      <Background variant={BackgroundVariant.Dots} gap={16} size={1} />
    </ReactFlow>
  );
}
