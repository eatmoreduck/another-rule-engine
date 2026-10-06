/**
 * FlowCanvas - React Flow 画布组件
 * 集成拖拽添加节点、连接节点、自定义节点渲染
 */

import { useCallback, type DragEvent } from 'react';
import {
  ReactFlow,
  Controls,
  ControlButton,
  Background,
  useReactFlow,
  type Connection,
  type NodeTypes,
  type OnNodesChange,
  type OnEdgesChange,
  BackgroundVariant,
} from '@xyflow/react';
import { PartitionOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type { FlowNode, FlowEdge } from '../../types/flowConfig';

import { genId } from '../../types/flowConfig';
import { getLayoutedElements } from '../../utils/flowLayout';

import StartNodeComponent from './nodes/StartNode';
import EndNodeComponent from './nodes/EndNode';
import ConditionNodeComponent from './nodes/ConditionNode';
import ActionNodeComponent from './nodes/ActionNode';
import RuleSetNodeComponent from './nodes/RuleSetNode';
import BlacklistNodeComponent from './nodes/BlacklistNode';
import WhitelistNodeComponent from './nodes/WhitelistNode';
import MergeNodeComponent from './nodes/MergeNode';
import WaypointEdgeComponent from './edges/WaypointEdge';

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

const edgeTypes = { waypoint: WaypointEdgeComponent };

/** 允许多条入边的节点类型（多路径汇入/合并分支） */
const MULTI_INLET_TYPES = new Set(['end', 'merge']);

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

function getDefaultBlacklistData() {
  return {
    label: '黑名单',
    nodeType: 'blacklist' as const,
    keyType: '',
  };
}

function getDefaultWhitelistData() {
  return {
    label: '白名单',
    nodeType: 'whitelist' as const,
    keyType: '',
  };
}

function getDefaultMergeData() {
  return {
    label: '合并分支',
    nodeType: 'merge' as const,
  };
}

interface FlowCanvasProps {
  nodes: FlowNode[];
  edges: FlowEdge[];
  onNodesChange: OnNodesChange<FlowNode>;
  onEdgesChange: OnEdgesChange<FlowEdge>;
  onConnect: (connection: Connection) => void;
  onNodeDoubleClick: (event: React.MouseEvent, node: FlowNode) => void;
  /** 一键排版：携带 ELK 计算后的节点与连线回调父页面（受控状态在父层） */
  onAutoLayout?: (nodes: FlowNode[], edges: FlowEdge[]) => void;
}

export default function FlowCanvas({
  nodes,
  edges,
  onNodesChange,
  onEdgesChange,
  onConnect: onConnectProp,
  onNodeDoubleClick,
  onAutoLayout,
}: FlowCanvasProps) {
  const { screenToFlowPosition, addNodes, fitView } = useReactFlow();
  const { t } = useTranslation();

  const handleAutoLayout = useCallback(() => {
    if (!onAutoLayout) return;
    getLayoutedElements(nodes, edges)
      .then((laidOut) => {
        onAutoLayout(laidOut.nodes, laidOut.edges);
        // 受控 nodes 更新进 store 后再缩放到全图
        window.setTimeout(() => fitView({ duration: 300, padding: 0.15 }), 60);
      })
      .catch((err) => console.error('自动布局失败:', err));
  }, [nodes, edges, onAutoLayout, fitView]);

  // 连线规则：禁自连/重复平行线；入边仅 end/merge 允许多条；
  // 出边仅 condition 允许两条（是/否各一），其余节点一条。
  const isValidConnection = useCallback(
    (conn: Connection) => {
      if (!conn.source || !conn.target || conn.source === conn.target) return false;
      const sameHandle = (a: string | null | undefined, b: string | null | undefined) => (a ?? null) === (b ?? null);
      if (
        edges.some(
          (e) =>
            e.source === conn.source &&
            sameHandle(e.sourceHandle, conn.sourceHandle) &&
            e.target === conn.target &&
            sameHandle(e.targetHandle, conn.targetHandle),
        )
      ) {
        return false;
      }
      const typeOf = (id?: string | null) => nodes.find((n) => n.id === id)?.data?.nodeType ?? '';
      if (!MULTI_INLET_TYPES.has(typeOf(conn.target)) && edges.some((e) => e.target === conn.target)) {
        return false;
      }
      if (typeOf(conn.source) === 'condition') {
        return !edges.some((e) => e.source === conn.source && sameHandle(e.sourceHandle, conn.sourceHandle));
      }
      return !edges.some((e) => e.source === conn.source);
    },
    [edges, nodes],
  );

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
      if (type === 'start') {
        newNode = {
          id: genId(),
          type: 'start',
          position,
          data: { label: '开始', nodeType: 'start' },
        };
      } else if (type === 'end') {
        newNode = {
          id: genId(),
          type: 'end',
          position,
          data: { label: '结束', nodeType: 'end', defaultAction: 'PASS', defaultReason: '默认通过' },
        };
      } else if (type === 'condition') {
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
      } else if (type === 'blacklist') {
        newNode = {
          id: genId(),
          type: 'blacklist',
          position,
          data: getDefaultBlacklistData(),
        };
      } else if (type === 'whitelist') {
        newNode = {
          id: genId(),
          type: 'whitelist',
          position,
          data: getDefaultWhitelistData(),
        };
      } else if (type === 'merge') {
        newNode = {
          id: genId(),
          type: 'merge',
          position,
          data: getDefaultMergeData(),
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
      isValidConnection={isValidConnection}
      onNodeDoubleClick={onNodeDoubleClick as never}
      nodeTypes={nodeTypes}
      edgeTypes={edgeTypes}
      defaultEdgeOptions={{ type: 'waypoint' }}
      fitView
      fitViewOptions={{ minZoom: 0.3, maxZoom: 0.8 }}
      minZoom={0.2}
      maxZoom={2}
      snapToGrid
      snapGrid={[15, 15] as [number, number]}
      deleteKeyCode={['Backspace', 'Delete']}
    >
      <Controls>
        <ControlButton onClick={handleAutoLayout} title={t('flowCanvas.autoLayout')}>
          <PartitionOutlined />
        </ControlButton>
      </Controls>
      <Background variant={BackgroundVariant.Dots} gap={16} size={1} />
    </ReactFlow>
  );
}
