import { useEffect, useState } from 'react';
import { Button, Input, Select, Space, Card, Breadcrumb, message, Popconfirm, Switch, Table, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, EyeOutlined, CheckCircleOutlined, StopOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useDecisionFlowStore } from '../stores/decisionFlowStore';
import { DecisionFlowStatus } from '../types/decisionFlow';
import type { DecisionFlow } from '../types/decisionFlow';
import { deleteDecisionFlow, enableDecisionFlow, disableDecisionFlow } from '../api/decisionFlows';
import Access from '../components/AccessControl';

export default function DecisionFlowListPage() {
  const navigate = useNavigate();
  const { loading, fetchFlows, currentParams, flows, total } = useDecisionFlowStore();
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<DecisionFlowStatus | undefined>();

  const loadData = () => {
    fetchFlows({ keyword: keyword || undefined, status: statusFilter, page: 0, size: 20 });
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleSearch = (value: string) => {
    setKeyword(value);
    fetchFlows({ keyword: value || undefined, status: statusFilter, page: 0, size: 20 });
  };

  const handleStatusChange = (value: DecisionFlowStatus | undefined) => {
    setStatusFilter(value);
    fetchFlows({ keyword: keyword || undefined, status: value, page: 0, size: 20 });
  };

  const handlePageChange = (page: number, pageSize: number) => {
    fetchFlows({ ...currentParams, page: page - 1, size: pageSize });
  };

  const handleDelete = async (flowKey: string) => {
    try {
      await deleteDecisionFlow(flowKey);
      message.success('删除成功');
      loadData();
    } catch {
      message.error('删除失败');
    }
  };

  const handleToggleEnabled = async (record: DecisionFlow) => {
    try {
      if (record.enabled) {
        await disableDecisionFlow(record.flowKey);
        message.success('已禁用');
      } else {
        await enableDecisionFlow(record.flowKey);
        message.success('已启用');
      }
      loadData();
    } catch {
      message.error('操作失败');
    }
  };

  const columns = [
    { title: 'Flow Key', dataIndex: 'flowKey', key: 'flowKey', render: (v: string) => <a onClick={() => navigate(`/decision-flows/${v}`)}>{v}</a> },
    { title: '名称', dataIndex: 'flowName', key: 'flowName' },
    {
      title: '状态', dataIndex: 'status', key: 'status',
      render: (status: DecisionFlowStatus) => {
        const colorMap: Record<string, string> = { DRAFT: 'default', ACTIVE: 'green', ARCHIVED: 'orange', DELETED: 'red' };
        const labelMap: Record<string, string> = { DRAFT: '草稿', ACTIVE: '生效中', ARCHIVED: '已归档', DELETED: '已删除' };
        return <Tag color={colorMap[status] ?? 'default'}>{labelMap[status] ?? status}</Tag>;
      },
    },
    { title: '版本', dataIndex: 'version', key: 'version' },
    {
      title: '启用', dataIndex: 'enabled', key: 'enabled',
      render: (enabled: boolean, record: DecisionFlow) => (
        <Access permission="api:decision-flows:update" fallback={
          <Tag color={enabled ? 'green' : 'default'}>{enabled ? '已启用' : '已禁用'}</Tag>
        }>
          <Switch
            size="small"
            checked={enabled}
            onChange={() => handleToggleEnabled(record)}
            checkedChildren={<CheckCircleOutlined />}
            unCheckedChildren={<StopOutlined />}
          />
        </Access>
      ),
    },
    { title: '创建人', dataIndex: 'createdBy', key: 'createdBy' },
    {
      title: '创建时间', dataIndex: 'createdAt', key: 'createdAt',
      render: (v: string) => v ? new Date(v).toLocaleString('zh-CN') : '-',
    },
    {
      title: '操作', key: 'actions',
      render: (_: unknown, record: DecisionFlow) => (
        <Space>
          <Button size="small" icon={<EyeOutlined />} onClick={() => navigate(`/decision-flows/${record.flowKey}`)}>详情</Button>
          <Access permission="api:decision-flows:update">
            <Button size="small" icon={<EditOutlined />} onClick={() => navigate(`/decision-flows/${record.flowKey}/edit`)}>编辑</Button>
          </Access>
          <Access permission="api:decision-flows:delete">
            <Popconfirm title="确认删除？" onConfirm={() => handleDelete(record.flowKey)}>
              <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
            </Popconfirm>
          </Access>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Breadcrumb style={{ marginBottom: 16 }} items={[{ title: '决策流' }]} />
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
          <Space>
            <Input.Search placeholder="搜索决策流名称或 Key" allowClear onSearch={handleSearch} style={{ width: 300 }} />
            <Select placeholder="状态筛选" allowClear style={{ width: 150 }} onChange={handleStatusChange}
              options={[
                { value: DecisionFlowStatus.DRAFT, label: '草稿' },
                { value: DecisionFlowStatus.ACTIVE, label: '已激活' },
                { value: DecisionFlowStatus.ARCHIVED, label: '已归档' },
              ]}
            />
          </Space>
          <Access permission="api:decision-flows:create">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/decision-flows/new')}>
              新建决策流
            </Button>
          </Access>
        </div>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={flows}
          loading={loading}
          pagination={{
            current: (currentParams.page ?? 0) + 1,
            pageSize: currentParams.size ?? 20,
            total,
            onChange: handlePageChange,
          }}
        />
      </Card>
    </>
  );
}
