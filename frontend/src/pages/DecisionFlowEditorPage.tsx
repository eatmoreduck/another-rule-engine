import { useState, useEffect, useCallback } from 'react';
import { Card, Button, Space, Breadcrumb, message, Spin, Typography, Input, Modal } from 'antd';
import { SaveOutlined } from '@ant-design/icons';
import { ReactFlowProvider, addEdge, useNodesState, useEdgesState, type Connection, type Node } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useNavigate, useParams, useBlocker } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getDecisionFlow, createDecisionFlow, updateDecisionFlow } from '../api/decisionFlows';
import { validateFeatureDefinitions } from '../api/featureCatalog';
import type { DecisionFlow } from '../types/decisionFlow';
import type { FlowNode, FlowEdge, ConditionNodeData, ActionNodeData, EndNodeData, RuleSetNodeData, BlacklistNodeData, WhitelistNodeData, MergeNodeData } from '../types/flowConfig';
import { withDefaultPositions } from '../utils/flowGraphLayout';
import type { FeatureValidationResponse } from '../types/featureCatalog';
import { createInitialNodes, createInitialEdges } from '../types/flowConfig';
import FlowCanvas from '../components/flow/FlowCanvas';
import NodePalette from '../components/flow/NodePalette';
import NodeConfigPanel from '../components/flow/NodeConfigPanel';

const { Title } = Typography;

function collectFlowFeatureItems(nodes: FlowNode[]) {
  return nodes
    .filter((node) => node.data?.nodeType === 'condition')
    .map((node) => ({
      fieldName: String(node.data?.fieldName ?? '').trim(),
      operator: String(node.data?.operator ?? ''),
      threshold: node.data?.threshold,
    }))
    .filter((item) => item.fieldName.length > 0);
}

function confirmFeatureWarnings(
  validation: FeatureValidationResponse,
  title: string,
  unknownFieldsLabel: string,
  warningsLabel: string,
  okText: string,
  cancelText: string,
): Promise<boolean> {
  return new Promise((resolve) => {
    Modal.confirm({
      title,
      width: 560,
      content: (
        <div>
          {validation.unknownFields.length > 0 && (
            <div style={{ marginBottom: 12 }}>
              <div style={{ fontWeight: 600, marginBottom: 6 }}>{unknownFieldsLabel}</div>
              <div>{validation.unknownFields.join(', ')}</div>
            </div>
          )}
          {validation.warnings.length > 0 && (
            <div>
              <div style={{ fontWeight: 600, marginBottom: 6 }}>{warningsLabel}</div>
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                {validation.warnings.slice(0, 6).map((warning) => (
                  <li key={warning}>{warning}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      ),
      okText,
      cancelText,
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
}

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
            if (graph.nodes) setNodes(withDefaultPositions(graph.nodes as FlowNode[]));
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
      const validationItems = collectFlowFeatureItems(nodes);
      if (validationItems.length > 0) {
        const validation = await validateFeatureDefinitions(validationItems);
        if (validation.warnings.length > 0 || validation.unknownFields.length > 0 || !validation.valid) {
          const shouldContinue = await confirmFeatureWarnings(
            validation,
            t('featureCatalog.validationWarningTitle'),
            t('featureCatalog.unknownFieldsLabel'),
            t('featureCatalog.warningsLabel'),
            t('featureCatalog.continueSave'),
            t('featureCatalog.backEdit'),
          );
          if (!shouldContinue) {
            return;
          }
        }
      }

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
        // 校验错误按 "; " 拆为多行展示；固定 key 使连点保存时替换而非堆叠
        const lines = err.message.split('; ').filter(Boolean);
        message.error({
          key: 'flow-save-error',
          duration: 8,
          content: (
            <div style={{ maxWidth: 640, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
              <div style={{ fontWeight: 600 }}>{t('rules.saveFailed')}</div>
              {lines.map((line, i) => (
                <div key={i} style={{ marginTop: i === 0 ? 6 : 2 }}>{line}</div>
              ))}
            </div>
          ),
        });
      }
    } finally {
      setSaving(false);
    }
  }, [edges, flowDescription, flowKey, flowKeyInput, flowName, isNew, navigate, nodes, t]);

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
              onEdgesChange={handleEdgesChange} onConnect={onConnect} onNodeDoubleClick={handleNodeDoubleClick}
              onAutoLayout={(n, e) => { setNodes(n); setEdges(e); setDirty(true); }} />
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
