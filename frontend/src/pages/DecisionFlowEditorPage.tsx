import { useState, useEffect, useCallback } from 'react';
import { Card, Button, Space, Breadcrumb, message, Spin, Typography, Input } from 'antd';
import { SaveOutlined } from '@ant-design/icons';
import { ReactFlowProvider, addEdge, useNodesState, useEdgesState, type Connection, type Node } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useNavigate, useParams, useBlocker } from 'react-router-dom';
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
        .catch(() => message.error('加载决策流失败'))
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
    return !window.confirm('有未保存的修改，确认离开？');
  });

  const handleSave = useCallback(async () => {
    if (isNew && !flowKeyInput.trim()) {
      message.error('请输入决策流标识');
      return;
    }
    if (!flowName.trim()) {
      message.error('请输入决策流名称');
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
        message.success('决策流创建成功');
        navigate(`/decision-flows/${created.flowKey}`);
      } else if (flowKey) {
        await updateDecisionFlow(flowKey, {
          flowName,
          flowDescription,
          flowGraph,
        });
        setDirty(false);
        message.success('决策流保存成功');
      }
    } catch (err) {
      if (err instanceof Error) {
        message.error(`保存失败: ${err.message}`);
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
          { title: <a onClick={() => navigate('/decision-flows')}>决策流</a> },
          ...(isNew
            ? [{ title: '新建决策流' }]
            : [
                { title: <a onClick={() => navigate(`/decision-flows/${flowKey}`)}>{existingFlow?.flowName ?? flowKey}</a> },
                { title: '编辑' },
              ]),
        ]}
      />
      <Card>
        <div className="page-header">
          <Title level={4} style={{ margin: 0 }}>
            {isNew ? '新建决策流' : `编辑决策流 - ${existingFlow?.flowName ?? flowKey}`}
          </Title>
          <div className="page-header-actions">
            <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>保存</Button>
          </div>
        </div>

        <div style={{ marginBottom: 16 }}>
          <Space wrap>
            {isNew && (
              <Input placeholder="决策流标识" value={flowKeyInput}
                onChange={(e) => { setFlowKeyInput(e.target.value); setDirty(true); }} style={{ width: 180 }} />
            )}
            <Input placeholder="决策流名称" value={flowName}
              onChange={(e) => { setFlowName(e.target.value); setDirty(true); }} style={{ width: 200 }} />
            <Input placeholder="描述（可选）" value={flowDescription}
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
                <Button size="small" block onClick={() => setSelectedNode(null)}>关闭面板</Button>
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
