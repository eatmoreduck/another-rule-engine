import { useState, useEffect, useCallback } from 'react';
import { Card, Button, Space, Breadcrumb, message, Spin, Typography, Input } from 'antd';
import { SaveOutlined } from '@ant-design/icons';
import { ReactFlowProvider, addEdge, useNodesState, useEdgesState, type Connection, type Node } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useNavigate, useParams, useBlocker } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getDecisionFlow, createDecisionFlow, updateDecisionFlow } from '../api/decisionFlows';
import type { DecisionFlow } from '../types/decisionFlow';
import type { FlowNode, FlowEdge, ConditionNodeData, ActionNodeData, EndNodeData, RuleSetNodeData, BlacklistNodeData, WhitelistNodeData, MergeNodeData } from '../types/flowConfig';
import { createInitialNodes, createInitialEdges } from '../types/flowConfig';
import FlowCanvas from '../components/flow/FlowCanvas';
import NodePalette from '../components/flow/NodePalette';
import NodeConfigPanel from '../components/flow/NodeConfigPanel';

const { Title } = Typography;

function FlowEditorInner() {
  const { flowKey } = useParams<{ flowKey: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const isNew = !flowKey || flowKey === 'new';

  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [existingFlow, setExistingFlow] = useState<DecisionFlow | null>(null);
  const [flowName, setFlowName] = useState('');
  const [flowDescription, setFlowDescription] = useState('');
  const [flowKeyInput, setFlowKeyInput] = useState('');

  const [nodes, setNodes, onNodesChange] = useNodesState<FlowNode>(createInitialNodes());
  const [edges, setEdges, onEdgesChange] = useEdgesState<FlowEdge>(createInitialEdges());
  const [selectedNode, setSelectedNode] = useState<FlowNode | null>(null);

  useEffect(() => {
    if (!isNew && flowKey) {
      setLoading(true);
      getDecisionFlow(flowKey)
        .then((flow) => {
          setExistingFlow(flow);
          setFlowName(flow.flowName);
          setFlowDescription(flow.flowDescription ?? '');
          try {
            const graph = JSON.parse(flow.flowGraph);
            if (graph.nodes) setNodes(graph.nodes as FlowNode[]);
            if (graph.edges) setEdges(graph.edges as FlowEdge[]);
          } catch { /* ignore parse error */ }
        })
        .catch(() => message.error(t('flows.loadFailed')))
        .finally(() => setLoading(false));
    }
  }, [isNew, flowKey, setNodes, setEdges]);

  const onConnect = useCallback((connection: Connection) => {
    setEdges((eds) => addEdge(connection, eds));
    setDirty(true);
  }, [setEdges]);

  const handleNodeDoubleClick = useCallback((_event: React.MouseEvent, node: Node) => {
    setSelectedNode(node as FlowNode);
  }, []);

  const handleNodesChange = useCallback((changes: Parameters<typeof onNodesChange>[0]) => {
    onNodesChange(changes);
    setDirty(true);
  }, [onNodesChange]);

  const handleEdgesChange = useCallback((changes: Parameters<typeof onEdgesChange>[0]) => {
    onEdgesChange(changes);
    setDirty(true);
  }, [onEdgesChange]);

  const handleNodeDataUpdate = useCallback((nodeId: string, updates: Partial<ConditionNodeData | ActionNodeData | EndNodeData | RuleSetNodeData | BlacklistNodeData | WhitelistNodeData | MergeNodeData>) => {
    setNodes((nds) =>
      nds.map((n) => {
        if (n.id === nodeId) {
          const updated = { ...n, data: { ...n.data, ...updates } } as FlowNode;
          setSelectedNode(updated);
          return updated;
        }
        return n;
      }) as FlowNode[],
    );
    setDirty(true);
  }, [setNodes]);

  useBlocker(({ currentLocation, nextLocation }) => {
    if (!dirty) return false;
    if (currentLocation.pathname === nextLocation.pathname) return false;
    return !window.confirm(t('common.confirmLeave'));
  });

  const handleSave = useCallback(async () => {
    if (isNew && !flowKeyInput.trim()) {
      message.error(t('flows.flowKeyRequired'));
      return;
    }
    if (!flowName.trim()) {
      message.error(t('flows.flowNameRequired'));
      return;
    }

    setSaving(true);
    const flowGraph = JSON.stringify({ nodes, edges });
    try {
      if (isNew) {
        const created = await createDecisionFlow({
          flowKey: flowKeyInput,
          flowName,
          flowDescription,
          flowGraph,
        });
        setDirty(false);
        message.success(t('flows.createSuccess'));
        navigate(`/decision-flows/${created.flowKey}`);
      } else if (flowKey) {
        await updateDecisionFlow(flowKey, {
          flowName,
          flowDescription,
          flowGraph,
        });
        setDirty(false);
        message.success(t('flows.saveSuccess'));
      }
    } catch (err) {
      if (err instanceof Error) {
        message.error(`${t('rules.saveFailed')}: ${err.message}`);
      }
    } finally {
      setSaving(false);
    }
  }, [isNew, flowKey, flowKeyInput, flowName, flowDescription, nodes, edges, navigate]);

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>;
  }

  return (
    <div>
      <Breadcrumb style={{ marginBottom: 16 }}
        items={[
          { title: <a onClick={() => navigate('/decision-flows')}>{t('flows.pageTitle')}</a> },
          ...(isNew
            ? [{ title: t('flows.createFlow') }]
            : [
                { title: <a onClick={() => navigate(`/decision-flows/${flowKey}`)}>{existingFlow?.flowName ?? flowKey}</a> },
                { title: t('common.edit') },
              ]),
        ]}
      />
      <Card>
        <div className="page-header">
          <Title level={4} style={{ margin: 0 }}>
            {isNew ? t('flows.createFlow') : `${t('flows.editFlow')} - ${existingFlow?.flowName ?? flowKey}`}
          </Title>
          <div className="page-header-actions">
            <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>{t('common.save')}</Button>
          </div>
        </div>

        <div style={{ marginBottom: 16 }}>
          <Space wrap>
            {isNew && (
              <Input placeholder={t('flows.flowKeyPlaceholder')} value={flowKeyInput}
                onChange={(e) => { setFlowKeyInput(e.target.value); setDirty(true); }} style={{ width: 180 }} />
            )}
            <Input placeholder={t('flows.flowNamePlaceholder')} value={flowName}
              onChange={(e) => { setFlowName(e.target.value); setDirty(true); }} style={{ width: 200 }} />
            <Input placeholder={t('flows.flowDescPlaceholder')} value={flowDescription}
              onChange={(e) => { setFlowDescription(e.target.value); setDirty(true); }} style={{ width: 300 }} />
          </Space>
        </div>

        <div style={{ display: 'flex', height: 'calc(100vh - 340px)', minHeight: 450, gap: 12 }}>
          <NodePalette />
          <div style={{ flex: 1, position: 'relative', border: '1px solid #e8e8e8', borderRadius: 8, overflow: 'hidden' }}>
            <FlowCanvas nodes={nodes} edges={edges} onNodesChange={handleNodesChange}
              onEdgesChange={handleEdgesChange} onConnect={onConnect} onNodeDoubleClick={handleNodeDoubleClick} />
          </div>
          {selectedNode && (
            <div style={{ width: 280, flexShrink: 0, display: 'flex', flexDirection: 'column' }}>
              <NodeConfigPanel node={selectedNode} onUpdate={handleNodeDataUpdate} />
              <div style={{ padding: '8px 12px' }}>
                <Button size="small" block onClick={() => setSelectedNode(null)}>{t('common.closePanel')}</Button>
              </div>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
}

export default function DecisionFlowEditorPage() {
  return (
    <ReactFlowProvider>
      <FlowEditorInner />
    </ReactFlowProvider>
  );
}
