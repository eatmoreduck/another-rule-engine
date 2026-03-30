/**
 * FlowEditorPage - 可视化流程图编辑页面
 * 布局：左侧 NodePalette + 中间 FlowCanvas + 右侧 NodeConfigPanel
 * 底部：Groovy 脚本实时预览面板
 * 外层包裹 ReactFlowProvider
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Card, Button, Space, Breadcrumb, message, Collapse, Spin, Typography, Input,
} from 'antd';
import {
  SaveOutlined, EyeOutlined,
} from '@ant-design/icons';
import {
  ReactFlowProvider,
  addEdge,
  useNodesState,
  useEdgesState,
  type Connection,
  type Node,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useNavigate, useParams, useBlocker } from 'react-router-dom';
import { getRule, createRule, updateRule } from '../api/rules';
import type { Rule } from '../types/rule';
import type {
  FlowNode, FlowEdge,
  ConditionNodeData, ActionNodeData, EndNodeData,
} from '../types/flowConfig';
import { createInitialNodes, createInitialEdges } from '../types/flowConfig';
import { generateGroovyFromFlow } from '../utils/flowDslGenerator';
import ModeSwitch from '../components/rules/ModeSwitch';
import ScriptPreview from '../components/rules/ScriptPreview';
import FlowCanvas from '../components/flow/FlowCanvas';
import NodePalette from '../components/flow/NodePalette';
import NodeConfigPanel from '../components/flow/NodeConfigPanel';

const { Title } = Typography;

/** 内部编辑器组件，必须在 ReactFlowProvider 内部 */
function FlowEditorInner() {
  const { ruleKey } = useParams<{ ruleKey: string }>();
  const navigate = useNavigate();
  const isNew = !ruleKey || ruleKey === 'new';

  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [existingRule, setExistingRule] = useState<Rule | null>(null);
  const [ruleName, setRuleName] = useState('');
  const [ruleDescription, setRuleDescription] = useState('');
  const [ruleKeyInput, setRuleKeyInput] = useState('');

  const [nodes, setNodes, onNodesChange] = useNodesState<FlowNode>(createInitialNodes());
  const [edges, setEdges, onEdgesChange] = useEdgesState<FlowEdge>(createInitialEdges());

  // 选中的节点（右侧配置面板）
  const [selectedNode, setSelectedNode] = useState<FlowNode | null>(null);

  // 加载已有规则
  useEffect(() => {
    if (!isNew && ruleKey) {
      setLoading(true);
      getRule(ruleKey)
        .then((rule) => {
          setExistingRule(rule);
          setRuleName(rule.ruleName);
          setRuleDescription(rule.ruleDescription ?? '');
        })
        .catch(() => message.error('加载规则失败'))
        .finally(() => setLoading(false));
    }
  }, [isNew, ruleKey]);

  // 连线事件
  const onConnect = useCallback(
    (connection: Connection) => {
      setEdges((eds) => addEdge(connection, eds));
      setDirty(true);
    },
    [setEdges],
  );

  // 双击节点 -> 打开配置面板
  const handleNodeDoubleClick = useCallback(
    (_event: React.MouseEvent, node: Node) => {
      setSelectedNode(node as FlowNode);
    },
    [],
  );

  // 节点变更
  const handleNodesChange = useCallback(
    (changes: Parameters<typeof onNodesChange>[0]) => {
      onNodesChange(changes);
      setDirty(true);
    },
    [onNodesChange],
  );

  // 边变更
  const handleEdgesChange = useCallback(
    (changes: Parameters<typeof onEdgesChange>[0]) => {
      onEdgesChange(changes);
      setDirty(true);
    },
    [onEdgesChange],
  );

  // 更新节点属性（来自 NodeConfigPanel）
  const handleNodeDataUpdate = useCallback(
    (nodeId: string, updates: Partial<ConditionNodeData | ActionNodeData | EndNodeData>) => {
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
    },
    [setNodes],
  );

  // 生成 Groovy 脚本
  const generatedScript = useMemo(() => {
    return generateGroovyFromFlow(nodes, edges);
  }, [nodes, edges]);

  // 离开页面确认
  useBlocker(
    ({ currentLocation, nextLocation }) => {
      if (dirty && currentLocation.pathname !== nextLocation.pathname) {
        return !window.confirm('有未保存的修改，确认离开？');
      }
      return false;
    },
  );

  // 保存
  const handleSave = useCallback(async () => {
    if (isNew && !ruleKeyInput.trim()) {
      message.error('请输入规则标识');
      return;
    }
    if (!ruleName.trim()) {
      message.error('请输入规则名称');
      return;
    }

    setSaving(true);
    try {
      if (isNew) {
        const created = await createRule({
          ruleKey: ruleKeyInput,
          ruleName,
          ruleDescription,
          groovyScript: generatedScript,
        });
        message.success('规则创建成功');
        navigate(`/rules/${created.ruleKey}`);
      } else if (ruleKey) {
        await updateRule(ruleKey, {
          ruleName,
          ruleDescription,
          groovyScript: generatedScript,
        });
        message.success('规则保存成功');
        navigate(`/rules/${ruleKey}`);
      }
      setDirty(false);
    } catch (err) {
      if (err instanceof Error) {
        message.error(`保存失败: ${err.message}`);
      }
    } finally {
      setSaving(false);
    }
  }, [isNew, ruleKey, ruleKeyInput, ruleName, ruleDescription, generatedScript, navigate]);

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div>
      <Breadcrumb
        style={{ marginBottom: 16 }}
        items={[
          { title: <a onClick={() => navigate('/rules')}>规则管理</a> },
          ...(isNew
            ? [{ title: '可视化新建' }]
            : [
                { title: <a onClick={() => navigate(`/rules/${ruleKey}`)}>{existingRule?.ruleName ?? ruleKey}</a> },
                { title: '可视化编辑' },
              ]),
        ]}
      />

      <Card>
        {/* 页面头部 */}
        <div className="page-header">
          <Title level={4} style={{ margin: 0 }}>
            {isNew ? '新建规则（流程图模式）' : `可视化编辑 - ${existingRule?.ruleName ?? ruleKey}`}
          </Title>
          <div className="page-header-actions">
            <ModeSwitch currentMode="flow" ruleKey={isNew ? undefined : ruleKey} />
            <Button icon={<EyeOutlined />} onClick={() => setPreviewOpen(true)}>
              预览脚本
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={saving}
              onClick={handleSave}
            >
              保存
            </Button>
          </div>
        </div>

        {/* 基本信息 */}
        <div style={{ marginBottom: 16 }}>
          <Space wrap>
            {isNew && (
              <Input
                placeholder="规则标识"
                value={ruleKeyInput}
                onChange={(e) => { setRuleKeyInput(e.target.value); setDirty(true); }}
                style={{ width: 180 }}
              />
            )}
            <Input
              placeholder="规则名称"
              value={ruleName}
              onChange={(e) => { setRuleName(e.target.value); setDirty(true); }}
              style={{ width: 200 }}
            />
            <Input
              placeholder="规则描述（可选）"
              value={ruleDescription}
              onChange={(e) => { setRuleDescription(e.target.value); setDirty(true); }}
              style={{ width: 300 }}
            />
          </Space>
        </div>

        {/* 编辑器主区域：左面板 + 画布 + 右面板 */}
        <div style={{ display: 'flex', height: 'calc(100vh - 340px)', minHeight: 450 }}>
          {/* 左侧节点拖拽面板 */}
          <NodePalette />

          {/* 中间 React Flow 画布 */}
          <div style={{ flex: 1, position: 'relative' }}>
            <FlowCanvas
              nodes={nodes}
              edges={edges}
              onNodesChange={handleNodesChange}
              onEdgesChange={handleEdgesChange}
              onConnect={onConnect}
              onNodeDoubleClick={handleNodeDoubleClick}
            />
          </div>

          {/* 右侧节点属性配置面板 */}
          {selectedNode && (
            <div style={{ width: 280, flexShrink: 0, display: 'flex', flexDirection: 'column' }}>
              <NodeConfigPanel
                node={selectedNode}
                onUpdate={handleNodeDataUpdate}
              />
              <div style={{ padding: '8px 12px' }}>
                <Button
                  size="small"
                  block
                  onClick={() => setSelectedNode(null)}
                >
                  关闭面板
                </Button>
              </div>
            </div>
          )}
        </div>

        {/* 底部脚本预览折叠面板 */}
        <Collapse
          style={{ marginTop: 16 }}
          items={[
            {
              key: 'preview',
              label: '实时生成的 Groovy 脚本',
              children: (
                <pre className="script-preview-code">{generatedScript}</pre>
              ),
            },
          ]}
        />
      </Card>

      {/* 脚本预览 Drawer */}
      <ScriptPreview
        open={previewOpen}
        onClose={() => setPreviewOpen(false)}
        script={generatedScript}
      />
    </div>
  );
}

/** 外层包裹 ReactFlowProvider */
export default function FlowEditorPage() {
  return (
    <ReactFlowProvider>
      <FlowEditorInner />
    </ReactFlowProvider>
  );
}
