import { useState, useEffect, useCallback } from 'react';
import { Card, Descriptions, Button, Space, Breadcrumb, Popconfirm, Switch, Typography, Spin, Tag, Alert } from 'antd';
import { EditOutlined, DeleteOutlined, ArrowLeftOutlined, CheckCircleOutlined, StopOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useParams, useNavigate } from 'react-router-dom';
import { ReactFlowProvider, ReactFlow, Controls, Background, BackgroundVariant, type NodeTypes } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useTranslation } from 'react-i18next';
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
import Access from '../components/AccessControl';

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
  const { t } = useTranslation();
  const [flow, setFlow] = useState<DecisionFlow | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [testModalOpen, setTestModalOpen] = useState(false);

  const [nodes] = useState<FlowNode[]>(createInitialNodes());
  const [edges] = useState<FlowEdge[]>(createInitialEdges());

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
          requestAnimationFrame(() => {
            if (graph.edges) setFlowEdges(graph.edges as FlowEdge[]);
          });
        }
      } catch { /* ignore parse error */ }
    } catch {
      setErrorMsg(t('flows.loadDetailFailed'));
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
      setErrorMsg(t('common.error'));
    }
  }, [flow]);

  const handleDelete = useCallback(async () => {
    if (!flow) return;
    try {
      await deleteDecisionFlow(flow.flowKey);
      navigate('/decision-flows');
    } catch {
      setErrorMsg(t('flows.deleteFailed'));
    }
  }, [flow, navigate]);

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>;
  }

  if (!flow) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Text type="secondary">{t('flows.notExist')}</Text><br />
        <Button style={{ marginTop: 16 }} onClick={() => navigate('/decision-flows')}>{t('common.returnList')}</Button>
      </div>
    );
  }

  const statusColorMap: Record<string, string> = { DRAFT: 'default', ACTIVE: 'green', ARCHIVED: 'orange', DELETED: 'red' };

  return (
    <div>
      <Breadcrumb style={{ marginBottom: 16 }}
        items={[
          { title: <a onClick={() => navigate('/decision-flows')}>{t('flows.pageTitle')}</a> },
          { title: flow.flowName },
        ]}
      />
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/decision-flows')}>{t('common.back')}</Button>
            <Typography.Title level={4} style={{ margin: 0 }}>{flow.flowName}</Typography.Title>
          </Space>
          <Space>
            <Access permission="api:decision-flows:view">
              <Button icon={<ThunderboltOutlined />} onClick={() => setTestModalOpen(true)}>{t('common.test')}</Button>
            </Access>
            <Access permission="api:decision-flows:update">
              <Button icon={<EditOutlined />} onClick={() => navigate(`/decision-flows/${flow.flowKey}/edit`)}>{t('common.edit')}</Button>
            </Access>
            <Access permission="api:decision-flows:delete">
              <Popconfirm title={t('flows.confirmDelete')} onConfirm={handleDelete} okText={t('flows.confirmDeleteAction')} cancelText={t('common.cancel')}>
                <Button danger icon={<DeleteOutlined />}>{t('common.delete')}</Button>
              </Popconfirm>
            </Access>
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
          <Descriptions.Item label={t('common.status')}>
            <Tag color={statusColorMap[flow.status] ?? 'default'}>{t(`flows.status.${flow.status}`)}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t('common.version')}>{flow.version}</Descriptions.Item>
          <Descriptions.Item label={t('common.enable')}>
            <Access permission="api:decision-flows:update" fallback={
              <Tag color={flow.enabled ? 'green' : 'default'}>{flow.enabled ? t('common.enabled') : t('common.disabled')}</Tag>
            }>
              <Switch checked={flow.enabled} onChange={handleToggleEnabled} size="small"
                checkedChildren={<CheckCircleOutlined />} unCheckedChildren={<StopOutlined />} />
            </Access>
          </Descriptions.Item>
        </Descriptions>

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
