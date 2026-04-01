import { useState, useEffect, useCallback } from 'react';
import { Card, Descriptions, Button, Space, Breadcrumb, Popconfirm, message, Collapse, Switch, Typography, Spin, Tag } from 'antd';
import { EditOutlined, DeleteOutlined, ArrowLeftOutlined, CheckCircleOutlined, StopOutlined } from '@ant-design/icons';
import { useParams, useNavigate } from 'react-router-dom';
import { getDecisionFlow, deleteDecisionFlow, enableDecisionFlow, disableDecisionFlow } from '../api/decisionFlows';
import type { DecisionFlow } from '../types/decisionFlow';

const { Text } = Typography;

export default function DecisionFlowDetailPage() {
  const { flowKey } = useParams<{ flowKey: string }>();
  const navigate = useNavigate();
  const [flow, setFlow] = useState<DecisionFlow | null>(null);
  const [loading, setLoading] = useState(true);

  const loadFlow = useCallback(async () => {
    if (!flowKey) return;
    setLoading(true);
    try {
      const data = await getDecisionFlow(flowKey);
      setFlow(data);
    } catch {
      message.error('加载决策流详情失败');
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
      message.success(flow.enabled ? '已禁用' : '已启用');
    } catch {
      message.error('操作失败');
    }
  }, [flow]);

  const handleDelete = useCallback(async () => {
    if (!flow) return;
    try {
      await deleteDecisionFlow(flow.flowKey);
      message.success('删除成功');
      navigate('/decision-flows');
    } catch {
      message.error('删除失败');
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
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/decision-flows')}>返回</Button>
            <Typography.Title level={4} style={{ margin: 0 }}>{flow.flowName}</Typography.Title>
          </Space>
          <Space>
            <Button icon={<EditOutlined />} onClick={() => navigate(`/decision-flows/${flow.flowKey}/edit`)}>编辑</Button>
            <Popconfirm title="确认删除此决策流？" onConfirm={handleDelete} okText="确认删除" cancelText="取消">
              <Button danger icon={<DeleteOutlined />}>删除</Button>
            </Popconfirm>
          </Space>
        </div>
        <Descriptions bordered column={2} style={{ marginBottom: 24 }}>
          <Descriptions.Item label="Flow Key">{flow.flowKey}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={statusColorMap[flow.status] ?? 'default'}>{statusLabelMap[flow.status] ?? flow.status}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="版本">{flow.version}</Descriptions.Item>
          <Descriptions.Item label="启用状态">
            <Switch checked={flow.enabled} onChange={handleToggleEnabled}
              checkedChildren={<CheckCircleOutlined />} unCheckedChildren={<StopOutlined />} />
          </Descriptions.Item>
          <Descriptions.Item label="创建人">{flow.createdBy}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{flow.createdAt ? new Date(flow.createdAt).toLocaleString('zh-CN') : '-'}</Descriptions.Item>
          <Descriptions.Item label="描述" span={2}>{flow.flowDescription ?? '-'}</Descriptions.Item>
        </Descriptions>
        <Collapse items={[{
          key: 'graph',
          label: '流程图数据 (JSON)',
          children: (
            <pre style={{ background: '#1e1e1e', color: '#d4d4d4', padding: 16, borderRadius: 8,
              fontFamily: "'Menlo','Monaco','Courier New',monospace", fontSize: 12, lineHeight: 1.5,
              overflow: 'auto', whiteSpace: 'pre-wrap', margin: 0 }}>
              {(() => { try { return JSON.stringify(JSON.parse(flow.flowGraph), null, 2); } catch { return flow.flowGraph; } })()}
            </pre>
          ),
        }]} />
      </Card>
    </div>
  );
}
