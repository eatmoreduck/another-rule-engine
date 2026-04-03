import { useState, useEffect, useCallback } from 'react';
import { Card, Descriptions, Button, Space, Breadcrumb, Popconfirm, Switch, Typography, Spin, Tag, Alert } from 'antd';
import { EditOutlined, DeleteOutlined, ArrowLeftOutlined, CheckCircleOutlined, StopOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useParams, useNavigate } from 'react-router-dom';
import { ReactFlowProvider, ReactFlow, Controls, Background, BackgroundVariant, type NodeTypes } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { getDecisionFlow, deleteDecisionFlow, enableDecisionFlow, disableDecisionFlow } from '../api/decisionFlows';
import type { DecisionFlow } from '../types/decisionFlow';
import type { FlowNode, FlowEdge } from '../types/flowConfig';
import { createInitialNodes, createInitialEdges } from '../types/flowConfig';
import StartNodeComponent from '../components/flow/nodes/StartNode';
import EndNodeComponent from '../components/flow/nodes/EndNode';
import ConditionNodeComponent from '../components/flow/nodes/ConditionNode';
import ActionNodeComponent from '../components/flow/nodes/ActionNode';
import RuleSetNodeComponent from '../components/flow/nodes/RuleSetNode';
import BlacklistNodeComponent from '../components/flow/nodes/BlacklistNode';
import WhitelistNodeComponent from '../components/flow/nodes/WhitelistNode';
import MergeNodeComponent from '../components/flow/nodes/MergeNode';
import DecisionFlowTestModal from '../components/flow/DecisionFlowTestModal';

const { Text } = Typography;

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

function DetailInner() {
  const { flowKey } = useParams<{ flowKey: string }>();
  const navigate = useNavigate();
  const [flow, setFlow] = useState<DecisionFlow | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [testModalOpen, setTestModalOpen] = useState(false);

  const [nodes] = useState<FlowNode[]>(createInitialNodes());
  const [edges] = useState<FlowEdge[]>(createInitialEdges());

  // 用于保存解析后的节点/边供 ReactFlow 使用
  const [flowNodes, setFlowNodes] = useState<FlowNode[]>(nodes);
  const [flowEdges, setFlowEdges] = useState<FlowEdge[]>(edges);

  const loadFlow = useCallback(async () => {
    if (!flowKey) return;
    setLoading(true);
    setErrorMsg('');
    try {
      const data = await getDecisionFlow(flowKey);
      setFlow(data);
      try {
        const graph = JSON.parse(data.flowGraph);
        if (graph.nodes) {
          setFlowNodes(graph.nodes as FlowNode[]);
          // 延迟设置 edges，确保自定义节点的 Handle DOM 已渲染并测量完毕
          // 否则带 sourceHandle 的边找不到 Handle bounds 会丢失
          requestAnimationFrame(() => {
            if (graph.edges) setFlowEdges(graph.edges as FlowEdge[]);
          });
        }
      } catch { /* ignore parse error */ }
    } catch {
      setErrorMsg('加载决策流详情失败');
    } finally {
      setLoading(false);
    }
  }, [flowKey]);

  useEffect(() => { loadFlow(); }, [loadFlow]);

  const handleToggleEnabled = useCallback(async () => {
    if (!flow) return;
    try {
      const updated = flow.enabled ? await disableDecisionFlow(flow.flowKey) : await enableDecisionFlow(flow.flowKey);
      setFlow(updated);
    } catch {
      setErrorMsg('操作失败');
    }
  }, [flow]);

  const handleDelete = useCallback(async () => {
    if (!flow) return;
    try {
      await deleteDecisionFlow(flow.flowKey);
      navigate('/decision-flows');
    } catch {
      setErrorMsg('删除失败');
    }
  }, [flow, navigate]);

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>;
  }

  if (!flow) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Text type="secondary">决策流不存在或加载失败</Text><br />
        <Button style={{ marginTop: 16 }} onClick={() => navigate('/decision-flows')}>返回列表</Button>
      </div>
    );
  }

  const statusColorMap: Record<string, string> = { DRAFT: 'default', ACTIVE: 'green', ARCHIVED: 'orange', DELETED: 'red' };
  const statusLabelMap: Record<string, string> = { DRAFT: '草稿', ACTIVE: '生效中', ARCHIVED: '已归档', DELETED: '已删除' };

  return (
    <div>
      <Breadcrumb style={{ marginBottom: 16 }}
        items={[
          { title: <a onClick={() => navigate('/decision-flows')}>决策流</a> },
          { title: flow.flowName },
        ]}
      />
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/decision-flows')}>返回</Button>
            <Typography.Title level={4} style={{ margin: 0 }}>{flow.flowName}</Typography.Title>
          </Space>
          <Space>
            <Button icon={<ThunderboltOutlined />} onClick={() => setTestModalOpen(true)}>测试</Button>
            <Button icon={<EditOutlined />} onClick={() => navigate(`/decision-flows/${flow.flowKey}/edit`)}>编辑</Button>
            <Popconfirm title="确认删除此决策流？" onConfirm={handleDelete} okText="确认删除" cancelText="取消">
              <Button danger icon={<DeleteOutlined />}>删除</Button>
            </Popconfirm>
          </Space>
        </div>

        {errorMsg && (
          <Alert type="error" message={errorMsg} showIcon closable onClose={() => setErrorMsg('')} style={{ marginBottom: 12 }} />
        )}

        <Descriptions bordered size="small" column={4} style={{ marginBottom: 12 }}
          labelStyle={{ width: 80, minWidth: 80, whiteSpace: 'nowrap' }}
          contentStyle={{ minWidth: 120 }}
        >
          <Descriptions.Item label="Flow Key">{flow.flowKey}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={statusColorMap[flow.status] ?? 'default'}>{statusLabelMap[flow.status] ?? flow.status}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="版本">{flow.version}</Descriptions.Item>
          <Descriptions.Item label="启用">
            <Switch checked={flow.enabled} onChange={handleToggleEnabled} size="small"
              checkedChildren={<CheckCircleOutlined />} unCheckedChildren={<StopOutlined />} />
          </Descriptions.Item>
        </Descriptions>

        {/* 流程图画布（只读） */}
        <div style={{ height: 'calc(100vh - 320px)', minHeight: 400, border: '1px solid #e8e8e8', borderRadius: 8, overflow: 'hidden' }}>
          <ReactFlow
            nodes={flowNodes}
            edges={flowEdges}
            nodeTypes={nodeTypes}
            fitView
            fitViewOptions={{ minZoom: 0.3, maxZoom: 0.8 }}
            nodesDraggable={false}
            nodesConnectable={false}
            edgesReconnectable={false}
            elementsSelectable={false}
            deleteKeyCode={null}
            minZoom={0.2}
            maxZoom={2}
          >
            <Controls showInteractive={false} />
            <Background variant={BackgroundVariant.Dots} gap={16} size={1} />
          </ReactFlow>
        </div>
      </Card>

      <DecisionFlowTestModal
        open={testModalOpen}
        onClose={() => setTestModalOpen(false)}
        flowKey={flow.flowKey}
        flowGraph={flow.flowGraph ?? ''}
      />
    </div>
  );
}

export default function DecisionFlowDetailPage() {
  return (
    <ReactFlowProvider>
      <DetailInner />
    </ReactFlowProvider>
  );
}
